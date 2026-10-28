package com.claimflow.claims.kafka;

import com.claimflow.claims.ClaimService;
import com.claimflow.events.ClaimAdjudicated;
import com.claimflow.events.Topics;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DecisionListener {

    private final ClaimService claims;

    public DecisionListener(ClaimService claims) {
        this.claims = claims;
    }

    @KafkaListener(topics = Topics.CLAIMS_ADJUDICATED, groupId = "claims")
    public void onDecision(ClaimAdjudicated decision) {
        MDC.put("claimId", decision.claimId());
        try {
            claims.applyDecision(decision);
        } finally {
            MDC.remove("claimId");
        }
    }
}
