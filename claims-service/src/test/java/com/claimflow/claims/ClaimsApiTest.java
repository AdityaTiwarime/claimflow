package com.claimflow.claims;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claimflow.claims.domain.ClaimRepository;
import com.claimflow.claims.domain.MemberRepository;
import com.claimflow.events.ClaimAdjudicated;
import com.claimflow.events.ClaimStatus;
import com.claimflow.events.ClaimSubmitted;
import com.claimflow.events.Topics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {Topics.CLAIMS_SUBMITTED, Topics.CLAIMS_ADJUDICATED})
class ClaimsApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EmbeddedKafkaBroker broker;
    @Autowired ClaimService service;
    @Autowired ClaimRepository claims;
    @Autowired MemberRepository members;

    private String body(String member, String type, String date, String amount) {
        return """
                {"memberNumber":"%s","serviceType":"%s","serviceDate":"%s","providerName":"City Clinic","billedAmount":%s}
                """.formatted(member, type, date, amount);
    }

    @Test
    void listsSeededMembersAndPlans() throws Exception {
        mvc.perform(get("/api/members")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5));
        mvc.perform(get("/api/plans")).andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value("GOLD"));
        mvc.perform(get("/api/members/M-1002")).andExpect(jsonPath("$.planCode").value("SILVER"));
    }

    @Test
    void acceptedClaimIsSavedAndPublishedToKafkaWithThePlanRules() throws Exception {
        Map<String, Object> props = KafkaTestUtils.consumerProps("api-test", "true", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, ClaimSubmitted> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new JsonDeserializer<>(ClaimSubmitted.class, false)).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, Topics.CLAIMS_SUBMITTED);

            String response = mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                            .content(body("M-1002", "LAB", "2026-03-10", "250.00")))
                    .andExpect(status().isAccepted())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.status").value("SUBMITTED"))
                    .andReturn().getResponse().getContentAsString();
            String id = json.readTree(response).get("id").asText();

            // Other tests in this class also submit claims; pick ours by its key (the claim ID).
            ConsumerRecord<String, ClaimSubmitted> rec = null;
            long deadline = System.currentTimeMillis() + 20_000;
            while (rec == null && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, ClaimSubmitted> r : KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2))) {
                    if (id.equals(r.key())) {
                        rec = r;
                    }
                }
            }
            assertThat(rec).as("record for " + id).isNotNull();
            assertThat(rec.value().planCode()).isEqualTo("SILVER");
            assertThat(rec.value().deductible()).isEqualByComparingTo("1500");
            assertThat(rec.value().billedAmount()).isEqualByComparingTo("250");
        }
    }

    @Test
    void invalidClaimsAreRejectedWithAReason() throws Exception {
        mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(body("M-1001", "LAB", "2026-03-10", "-5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("billedAmount must be greater than or equal to 0.01"));
        mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(body("M-1001", "SURGERY", "2026-03-10", "100")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(body("M-1001", "LAB", "2999-01-01", "100")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("serviceDate must be a date in the past or in the present"));
    }

    @Test
    void unknownMemberOrClaimIs404() throws Exception {
        mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(body("M-9999", "LAB", "2026-03-10", "100")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No member M-9999"));
        mvc.perform(get("/api/claims/CLM-NOPE")).andExpect(status().isNotFound());
    }

    @Test
    void approvedDecisionUpdatesTheMembersTotalsExactlyOnce() throws Exception {
        String response = mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(body("M-1005", "SPECIALIST", "2026-04-02", "1000.00")))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(response).get("id").asText();

        ClaimAdjudicated decision = new ClaimAdjudicated(id, ClaimStatus.APPROVED, new BigDecimal("450.00"),
                new BigDecimal("550.00"), new BigDecimal("500.00"), List.of("DEDUCTIBLE_APPLIED"), Instant.now());
        service.applyDecision(decision);
        service.applyDecision(decision); // Kafka may redeliver: must not count twice

        var member = members.findByMemberNumber("M-1005").orElseThrow();
        assertThat(member.getDeductibleMet()).isEqualByComparingTo("500");
        assertThat(member.getBenefitsUsed()).isEqualByComparingTo("450");

        JsonNode view = json.readTree(mvc.perform(get("/api/claims/" + id)).andReturn().getResponse().getContentAsString());
        assertThat(view.get("status").asText()).isEqualTo("APPROVED");
        assertThat(view.get("planPays").decimalValue()).isEqualByComparingTo("450");
        assertThat(view.get("timeline").findValuesAsText("type")).contains("SUBMITTED", "APPROVED");
    }

    @Test
    void decisionForAnUnknownClaimIsIgnored() {
        service.applyDecision(new ClaimAdjudicated("CLM-GHOST", ClaimStatus.APPROVED, BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.ZERO, List.of(), Instant.now()));
        assertThat(claims.findById("CLM-GHOST")).isEmpty();
    }
}
