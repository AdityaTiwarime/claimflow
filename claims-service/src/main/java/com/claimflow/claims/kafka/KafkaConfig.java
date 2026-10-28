package com.claimflow.claims.kafka;

import com.claimflow.events.Topics;
import java.time.Clock;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
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

    /** Retry a failing decision 3 times (for example a database blip), then log and skip it. */
    @Bean
    DefaultErrorHandler errorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1000L, 3));
    }
}
