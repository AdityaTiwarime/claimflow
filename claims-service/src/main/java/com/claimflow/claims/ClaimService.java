package com.claimflow.claims;

import com.claimflow.claims.api.NotFoundException;
import com.claimflow.claims.api.SubmitClaimRequest;
import com.claimflow.claims.domain.Claim;
import com.claimflow.claims.domain.ClaimRepository;
import com.claimflow.claims.domain.Member;
import com.claimflow.claims.domain.MemberRepository;
import com.claimflow.claims.domain.Plan;
import com.claimflow.claims.timeline.ClaimTimeline;
import com.claimflow.claims.timeline.TimelineEntry;
import com.claimflow.events.ClaimAdjudicated;
import com.claimflow.events.ClaimSubmitted;
import java.security.SecureRandom;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);
    private static final String ID_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberRepository members;
    private final ClaimRepository claims;
    private final ClaimTimeline timeline;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ClaimService(MemberRepository members, ClaimRepository claims, ClaimTimeline timeline,
                        ApplicationEventPublisher events, Clock clock) {
        this.members = members;
        this.claims = claims;
        this.timeline = timeline;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Saves the claim as SUBMITTED. The Kafka message is sent only after the database transaction
     * commits (see ClaimPublisher), so adjudication never sees a claim that was rolled back.
     */
    @Transactional
    public Claim submit(SubmitClaimRequest req) {
        Member m = members.findByMemberNumber(req.memberNumber())
                .orElseThrow(() -> new NotFoundException("No member " + req.memberNumber()));
        Claim claim = claims.save(new Claim(newId(), m, req.serviceType(), req.serviceDate(), req.providerName().trim(),
                req.billedAmount(), clock.instant()));
        Plan p = m.getPlan();
        events.publishEvent(new ClaimSubmitted(
                claim.getId(), m.getMemberNumber(), claim.getServiceType(), claim.getServiceDate(), claim.getBilledAmount(),
                m.getCoverageStart(), m.getCoverageEnd(), p.getCode(), p.getCoveredServices(), p.getDeductible(),
                m.getDeductibleMet(), p.getCoinsurancePercent(), p.getAnnualMax(), m.getBenefitsUsed()));
        timeline.record(new TimelineEntry(claim.getId(), clock.instant(), "SUBMITTED",
                "Claim for " + claim.getBilledAmount() + " (" + claim.getServiceType() + ") received"));
        log.info("claim {} submitted for member {} amount {}", claim.getId(), m.getMemberNumber(), claim.getBilledAmount());
        return claim;
    }

    /**
     * Applies a decision from the adjudication service. Kafka delivers at least once, so the same
     * decision may arrive twice: a claim that is already final is left alone, which keeps the
     * member's totals from being counted twice.
     */
    @Transactional
    public void applyDecision(ClaimAdjudicated d) {
        Claim claim = claims.findById(d.claimId()).orElse(null);
        if (claim == null) {
            log.warn("decision for unknown claim {} ignored", d.claimId());
            return;
        }
        if (claim.getStatus().isFinal()) {
            log.info("duplicate decision for claim {} ignored (already {})", claim.getId(), claim.getStatus());
            return;
        }
        claim.decide(d);
        if (d.status() == com.claimflow.events.ClaimStatus.APPROVED) {
            claim.getMember().applyApprovedClaim(d.deductibleApplied(), d.planPays());
        }
        String detail = switch (d.status()) {
            case APPROVED -> "Plan pays " + d.planPays() + ", member pays " + d.memberPays();
            case DENIED -> "Denied: " + String.join(", ", d.reasonCodes());
            case PENDED -> "Sent for manual review: " + String.join(", ", d.reasonCodes());
            case SUBMITTED -> "";
        };
        timeline.record(new TimelineEntry(claim.getId(), clock.instant(), d.status().name(), detail));
        log.info("claim {} {} planPays={} memberPays={}", claim.getId(), d.status(), d.planPays(), d.memberPays());
    }

    static String newId() {
        StringBuilder sb = new StringBuilder("CLM-");
        for (int i = 0; i < 8; i++) {
            sb.append(ID_CHARS.charAt(RANDOM.nextInt(ID_CHARS.length())));
        }
        return sb.toString();
    }
}
