package com.claimflow.adjudication;

import com.claimflow.events.ClaimAdjudicated;
import com.claimflow.events.ClaimSubmitted;
import com.claimflow.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Reads submitted claims, decides them, publishes the decision keyed by claim ID. */
@Component
public class ClaimListener {

    private static final Logger log = LoggerFactory.getLogger(ClaimListener.class);

    private final AdjudicationEngine engine;
    private final KafkaTemplate<String, Object> kafka;

    public ClaimListener(AdjudicationEngine engine, KafkaTemplate<String, Object> kafka) {
        this.engine = engine;
        this.kafka = kafka;
    }

    @KafkaListener(topics = Topics.CLAIMS_SUBMITTED, groupId = "adjudication")
    public void onClaimSubmitted(ClaimSubmitted claim) throws Exception {
        MDC.put("claimId", claim.claimId());
        try {
            ClaimAdjudicated decision = engine.adjudicate(claim);
            // Wait for the broker to acknowledge, so a failed send is retried instead of lost.
            kafka.send(Topics.CLAIMS_ADJUDICATED, decision.claimId(), decision).get();
            log.info("claim {} {} planPays={} memberPays={} reasons={}",
                    decision.claimId(), decision.status(), decision.planPays(), decision.memberPays(), decision.reasonCodes());
        } finally {
            MDC.remove("claimId");
        }
    }
}
