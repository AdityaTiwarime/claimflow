package com.claimflow.claims.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A covered person, with this year's running totals ("accumulators"): how much of the deductible
 * they have paid and how much the plan has paid so far.
 */
@Entity
@Table(name = "members")
public class Member {
    @Id private Long id;
    private String memberNumber;
    private String fullName;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    private LocalDate coverageStart;
    private LocalDate coverageEnd;
    private BigDecimal deductibleMet;
    private BigDecimal benefitsUsed;

    /** Optimistic locking: two decisions updating the same member can't overwrite each other. */
    @Version private long version;

    protected Member() {}

    /** Adds an approved claim's amounts to this year's totals. */
    public void applyApprovedClaim(BigDecimal deductibleApplied, BigDecimal planPaid) {
        deductibleMet = deductibleMet.add(deductibleApplied).min(plan.getDeductible());
        benefitsUsed = benefitsUsed.add(planPaid);
    }

    public Long getId() { return id; }
    public String getMemberNumber() { return memberNumber; }
    public String getFullName() { return fullName; }
    public Plan getPlan() { return plan; }
    public LocalDate getCoverageStart() { return coverageStart; }
    public LocalDate getCoverageEnd() { return coverageEnd; }
    public BigDecimal getDeductibleMet() { return deductibleMet; }
    public BigDecimal getBenefitsUsed() { return benefitsUsed; }
}
