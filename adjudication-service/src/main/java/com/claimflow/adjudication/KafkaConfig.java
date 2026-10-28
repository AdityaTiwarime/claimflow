package com.claimflow.adjudication;

import com.claimflow.events.Topics;
import java.time.Clock;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    NewTopic claimsSubmitted() {
        return TopicBuilder.name(Topics.CLAIMS_SUBMITTED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic claimsAdjudicated() {
        return TopicBuilder.name(Topics.CLAIMS_ADJUDICATED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic claimsSubmittedDlt() {
        return TopicBuilder.name(Topics.CLAIMS_SUBMITTED_DLT).partitions(1).replicas(1).build();
    }

    /**
     * A message that fails is retried 3 times, 1 s apart, then moved to claims.submitted.DLT, so one
     * bad message can't block the whole partition.
     */
    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> template) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), new FixedBackOff(1000L, 3));
    }
}
