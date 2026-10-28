package com.claimflow.claims.api;

import com.claimflow.claims.domain.Claim;
import com.claimflow.claims.domain.Member;
import com.claimflow.claims.domain.Plan;
import com.claimflow.claims.timeline.TimelineEntry;
import com.claimflow.events.ClaimStatus;
import com.claimflow.events.ServiceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** JSON shapes returned by the API (entities are never serialised directly). */
public final class Views {
    private Views() {}

    public record PlanView(String code, String name, BigDecimal deductible, int coinsurancePercent,
                           BigDecimal annualMax, Set<ServiceType> coveredServices) {
        static PlanView of(Plan p) {
            return new PlanView(p.getCode(), p.getName(), p.getDeductible(), p.getCoinsurancePercent(),
                    p.getAnnualMax(), p.getCoveredServices());
        }
    }

    public record MemberView(String memberNumber, String fullName, String planCode, LocalDate coverageStart,
                             LocalDate coverageEnd, BigDecimal deductibleMet, BigDecimal benefitsUsed) {
        static MemberView of(Member m) {
            return new MemberView(m.getMemberNumber(), m.getFullName(), m.getPlan().getCode(), m.getCoverageStart(),
                    m.getCoverageEnd(), m.getDeductibleMet(), m.getBenefitsUsed());
        }
    }

    public record ClaimView(String id, String memberNumber, String memberName, ServiceType serviceType,
                            LocalDate serviceDate, String providerName, BigDecimal billedAmount, ClaimStatus status,
                            BigDecimal planPays, BigDecimal memberPays, BigDecimal deductibleApplied,
                            List<String> reasonCodes, Instant submittedAt, Instant decidedAt,
                            List<TimelineEntry> timeline) {
        static ClaimView of(Claim c, List<TimelineEntry> timeline) {
            List<String> reasons = c.getReasonCodes() == null || c.getReasonCodes().isBlank()
                    ? List.of() : Arrays.asList(c.getReasonCodes().split(","));
            return new ClaimView(c.getId(), c.getMember().getMemberNumber(), c.getMember().getFullName(),
                    c.getServiceType(), c.getServiceDate(), c.getProviderName(), c.getBilledAmount(), c.getStatus(),
                    c.getPlanPays(), c.getMemberPays(), c.getDeductibleApplied(), reasons, c.getSubmittedAt(),
                    c.getDecidedAt(), timeline);
        }
    }

    public record StatusSummary(ClaimStatus status, long count, BigDecimal billed, BigDecimal planPaid) {}
}
