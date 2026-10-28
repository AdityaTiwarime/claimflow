package com.claimflow.claims;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.claimflow.events.Topics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

/** Both services, real Kafka round trip: submit over HTTP, wait for the decision to come back. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 3, topics = {Topics.CLAIMS_SUBMITTED, Topics.CLAIMS_ADJUDICATED, Topics.CLAIMS_SUBMITTED_DLT})
class FullFlowTest {

    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;

    @BeforeEach
    void startAdjudication() {
        AdjudicationRunner.ensureStarted();
    }

    private JsonNode submitAndWait(String member, String type, String amount) throws Exception {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"memberNumber":"%s","serviceType":"%s","serviceDate":"2026-03-15","providerName":"Apollo Clinic","billedAmount":%s}
                """.formatted(member, type, amount);
        String id = json.readTree(http.postForObject("/api/claims", new HttpEntity<>(body, h), String.class)).get("id").asText();
        JsonNode[] result = new JsonNode[1];
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).until(() -> {
            result[0] = json.readTree(http.getForObject("/api/claims/" + id, String.class));
            return !"SUBMITTED".equals(result[0].get("status").asText());
        });
        return result[0];
    }

    @Test
    void goldMemberWithDeductibleMetGets90PercentPaid() throws Exception {
        JsonNode c = submitAndWait("M-1001", "SPECIALIST", "2000.00");
        assertThat(c.get("status").asText()).isEqualTo("APPROVED");
        assertThat(c.get("planPays").decimalValue()).isEqualByComparingTo("1800");
        assertThat(c.get("memberPays").decimalValue()).isEqualByComparingTo("200");
        assertThat(c.get("timeline").findValuesAsText("type")).containsSubsequence("SUBMITTED", "APPROVED");
    }

    @Test
    void memberWhoseCoverageEndedIsDenied() throws Exception {
        JsonNode c = submitAndWait("M-1004", "LAB", "300.00");
        assertThat(c.get("status").asText()).isEqualTo("DENIED");
        assertThat(c.get("reasonCodes").get(0).asText()).isEqualTo("COVERAGE_INACTIVE");
    }

    @Test
    void basicPlanDoesNotCoverImaging() throws Exception {
        JsonNode c = submitAndWait("M-1003", "IMAGING", "800.00");
        assertThat(c.get("status").asText()).isEqualTo("DENIED");
        assertThat(c.get("reasonCodes").get(0).asText()).isEqualTo("SERVICE_NOT_COVERED");
    }

    @Test
    void veryLargeClaimGoesToManualReview() throws Exception {
        assertThat(submitAndWait("M-1002", "EMERGENCY", "75000.00").get("status").asText()).isEqualTo("PENDED");
    }
}
