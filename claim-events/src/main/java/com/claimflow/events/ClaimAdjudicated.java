package com.claimflow.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The decision on a claim. Amounts always add up: planPays + memberPays = billed amount for an
 * approved claim; both are zero for a denied or pended one.
 */
public record ClaimAdjudicated(
        String claimId,
        ClaimStatus status,
        BigDecimal planPays,
        BigDecimal memberPays,
        BigDecimal deductibleApplied,
        List<String> reasonCodes,
        Instant decidedAt) {}
