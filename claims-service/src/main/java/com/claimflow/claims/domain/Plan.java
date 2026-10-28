package com.claimflow.claims.domain;

import com.claimflow.events.ServiceType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Set;

/** A health plan's benefit rules. Read-only in this app (seeded by Flyway). */
@Entity
@Table(name = "plans")
public class Plan {
    @Id private Long id;
    private String code;
    private String name;
    private BigDecimal deductible;
    private int coinsurancePercent;
    private BigDecimal annualMax;

    @Convert(converter = ServiceTypesConverter.class)
    @Column(name = "covered_services")
    private Set<ServiceType> coveredServices;

    protected Plan() {}

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public BigDecimal getDeductible() { return deductible; }
    public int getCoinsurancePercent() { return coinsurancePercent; }
    public BigDecimal getAnnualMax() { return annualMax; }
    public Set<ServiceType> getCoveredServices() { return coveredServices; }
}
