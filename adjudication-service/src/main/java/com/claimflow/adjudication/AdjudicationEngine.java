package com.claimflow.adjudication;

import com.claimflow.events.ClaimAdjudicated;
import com.claimflow.events.ClaimStatus;
import com.claimflow.events.ClaimSubmitted;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The benefit rules, as a pure function of the claim: no database, no Kafka, so every rule is
 * unit-tested directly. Rules, in order:
 *
 * <ol>
 *   <li>Service date outside the member's coverage → DENIED (COVERAGE_INACTIVE).
 *   <li>Service type not covered by the plan → DENIED (SERVICE_NOT_COVERED).
 *   <li>Billed amount at or above the review threshold → PENDED (MANUAL_REVIEW).
 *   <li>Otherwise APPROVED: the member pays the remaining deductible first, then their coinsurance
 *       share of the rest; the plan pays the remainder, capped at what is left of the annual maximum
 *       (anything over the cap moves to the member, ANNUAL_MAX_REACHED).
 * </ol>
 */
@Component
public class AdjudicationEngine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BigDecimal reviewThreshold;
    private final Clock clock;

    public AdjudicationEngine(@Value("${claimflow.review-threshold:50000}") BigDecimal reviewThreshold, Clock clock) {
        this.reviewThreshold = reviewThreshold;
        this.clock = clock;
    }

    public ClaimAdjudicated adjudicate(ClaimSubmitted c) {
        if (c.serviceDate().isBefore(c.coverageStart())
                || (c.coverageEnd() != null && c.serviceDate().isAfter(c.coverageEnd()))) {
            return decided(c, ClaimStatus.DENIED, List.of("COVERAGE_INACTIVE"));
        }
        if (!c.coveredServices().contains(c.serviceType())) {
            return decided(c, ClaimStatus.DENIED, List.of("SERVICE_NOT_COVERED"));
        }
        if (c.billedAmount().compareTo(reviewThreshold) >= 0) {
            return decided(c, ClaimStatus.PENDED, List.of("MANUAL_REVIEW"));
        }

        List<String> reasons = new ArrayList<>();
        BigDecimal billed = c.billedAmount();

        BigDecimal deductibleLeft = c.deductible().subtract(c.deductibleMet()).max(BigDecimal.ZERO);
        BigDecimal deductibleApplied = billed.min(deductibleLeft);
        if (deductibleApplied.signum() > 0) {
            reasons.add("DEDUCTIBLE_APPLIED");
        }

        BigDecimal afterDeductible = billed.subtract(deductibleApplied);
        BigDecimal coinsurance = money(afterDeductible.multiply(BigDecimal.valueOf(c.coinsurancePercent())).divide(HUNDRED));
        BigDecimal planPays = afterDeductible.subtract(coinsurance);

        BigDecimal benefitLeft = c.annualMax().subtract(c.benefitsUsed()).max(BigDecimal.ZERO);
        if (planPays.compareTo(benefitLeft) > 0) {
            planPays = benefitLeft;
            reasons.add("ANNUAL_MAX_REACHED");
        }
        BigDecimal memberPays = billed.subtract(planPays);

        return new ClaimAdjudicated(
                c.claimId(),
                ClaimStatus.APPROVED,
                money(planPays),
                money(memberPays),
                money(deductibleApplied),
                List.copyOf(reasons),
                clock.instant());
    }

    private ClaimAdjudicated decided(ClaimSubmitted c, ClaimStatus status, List<String> reasons) {
        return new ClaimAdjudicated(
                c.claimId(), status, money(BigDecimal.ZERO), money(BigDecimal.ZERO), money(BigDecimal.ZERO), reasons, clock.instant());
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
