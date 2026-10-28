package com.claimflow.adjudication;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimflow.events.ClaimAdjudicated;
import com.claimflow.events.ClaimStatus;
import com.claimflow.events.ClaimSubmitted;
import com.claimflow.events.ServiceType;
import com.claimflow.events.Topics;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/** The real listener against an in-memory Kafka broker: a claim goes in, a decision comes out. */
@SpringBootTest(properties = {AdjudicationApplication.CONFIG_NAME, "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}", "server.port=0"})
@EmbeddedKafka(partitions = 1, topics = {Topics.CLAIMS_SUBMITTED, Topics.CLAIMS_ADJUDICATED, Topics.CLAIMS_SUBMITTED_DLT})
class ClaimListenerKafkaTest {

    @Autowired KafkaTemplate<String, Object> template;
    @Autowired EmbeddedKafkaBroker broker;

    @Test
    void publishesADecisionForEverySubmittedClaim() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("test", "true", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        JsonDeserializer<ClaimAdjudicated> json = new JsonDeserializer<>(ClaimAdjudicated.class, false);
        try (Consumer<String, ClaimAdjudicated> consumer =
                new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), json).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, Topics.CLAIMS_ADJUDICATED);

            template.send(Topics.CLAIMS_SUBMITTED, "C-42", new ClaimSubmitted(
                    "C-42", "M-1001", ServiceType.LAB, LocalDate.of(2026, 3, 1), new BigDecimal("100"),
                    LocalDate.of(2026, 1, 1), null, "GOLD", EnumSet.of(ServiceType.LAB),
                    new BigDecimal("0"), new BigDecimal("0"), 20, new BigDecimal("10000"), new BigDecimal("0")));

            ConsumerRecord<String, ClaimAdjudicated> rec =
                    KafkaTestUtils.getSingleRecord(consumer, Topics.CLAIMS_ADJUDICATED, Duration.ofSeconds(20));
            assertThat(rec.key()).isEqualTo("C-42");
            assertThat(rec.value().status()).isEqualTo(ClaimStatus.APPROVED);
            assertThat(rec.value().planPays()).isEqualByComparingTo("80");
        }
    }
}
