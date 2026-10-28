package com.claimflow.claims.domain;

import com.claimflow.events.ClaimAdjudicated;
import com.claimflow.events.ClaimStatus;
import com.claimflow.events.ServiceType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "claims")
public class Claim {
    @Id private String id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    @Enumerated(EnumType.STRING)
    private ServiceType serviceType;

    private LocalDate serviceDate;
    private String providerName;
    private BigDecimal billedAmount;

    @Enumerated(EnumType.STRING)
    private ClaimStatus status;

    private BigDecimal planPays;
    private BigDecimal memberPays;
    private BigDecimal deductibleApplied;
    private String reasonCodes;
    private Instant submittedAt;
    private Instant decidedAt;

    @Version private long version;

    protected Claim() {}

    public Claim(String id, Member member, ServiceType serviceType, LocalDate serviceDate, String providerName,
                 BigDecimal billedAmount, Instant submittedAt) {
        this.id = id;
        this.member = member;
        this.serviceType = serviceType;
        this.serviceDate = serviceDate;
        this.providerName = providerName;
        this.billedAmount = billedAmount;
        this.submittedAt = submittedAt;
        this.status = ClaimStatus.SUBMITTED;
    }

    /** Records the adjudication decision. */
    public void decide(ClaimAdjudicated d) {
        status = d.status();
        planPays = d.planPays();
        memberPays = d.memberPays();
        deductibleApplied = d.deductibleApplied();
        reasonCodes = String.join(",", d.reasonCodes());
        decidedAt = d.decidedAt();
    }

    public String getId() { return id; }
    public Member getMember() { return member; }
    public ServiceType getServiceType() { return serviceType; }
    public LocalDate getServiceDate() { return serviceDate; }
    public String getProviderName() { return providerName; }
    public BigDecimal getBilledAmount() { return billedAmount; }
    public ClaimStatus getStatus() { return status; }
    public BigDecimal getPlanPays() { return planPays; }
    public BigDecimal getMemberPays() { return memberPays; }
    public BigDecimal getDeductibleApplied() { return deductibleApplied; }
    public String getReasonCodes() { return reasonCodes; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
