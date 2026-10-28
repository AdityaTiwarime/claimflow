package com.claimflow.claims.kafka;

import com.claimflow.claims.timeline.ClaimTimeline;
import com.claimflow.claims.timeline.TimelineEntry;
import com.claimflow.events.ClaimSubmitted;
import com.claimflow.events.Topics;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends ClaimSubmitted to Kafka once the claim is safely committed to MySQL. */
@Component
public class ClaimPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClaimPublisher.class);

    private final KafkaTemplate<String, Object> kafka;
    private final ClaimTimeline timeline;
    private final Clock clock;

    public ClaimPublisher(KafkaTemplate<String, Object> kafka, ClaimTimeline timeline, Clock clock) {
        this.kafka = kafka;
        this.timeline = timeline;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(ClaimSubmitted event) {
        // Keyed by claim ID: all messages about one claim land on the same partition, in order.
        kafka.send(Topics.CLAIMS_SUBMITTED, event.claimId(), event).whenComplete((result, error) -> {
            if (error != null) {
                log.error("could not publish claim {}: {}", event.claimId(), error.getMessage());
                timeline.record(new TimelineEntry(event.claimId(), clock.instant(), "PUBLISH_FAILED", error.getMessage()));
            } else {
                timeline.record(new TimelineEntry(event.claimId(), clock.instant(), "SENT_FOR_ADJUDICATION",
                        "Kafka " + result.getRecordMetadata().topic() + " partition " + result.getRecordMetadata().partition()));
            }
        });
    }
}
