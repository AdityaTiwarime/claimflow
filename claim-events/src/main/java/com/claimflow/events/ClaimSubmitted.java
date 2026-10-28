package com.claimflow.events;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Everything the adjudication service needs to decide a claim, so it never has to call back
 * into the claims database: the claim itself, the member's coverage and the plan's benefit rules,
 * as they stood when the claim was submitted.
 */
public record ClaimSubmitted(
        String claimId,
        String memberNumber,
        ServiceType serviceType,
        LocalDate serviceDate,
        BigDecimal billedAmount,
        LocalDate coverageStart,
        LocalDate coverageEnd,
        String planCode,
        Set<ServiceType> coveredServices,
        BigDecimal deductible,
        BigDecimal deductibleMet,
        int coinsurancePercent,
        BigDecimal annualMax,
        BigDecimal benefitsUsed) {}
