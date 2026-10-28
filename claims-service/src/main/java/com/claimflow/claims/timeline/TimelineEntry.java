package com.claimflow.claims.timeline;

import java.time.Instant;

/** One step in a claim's history ("submitted", "sent for adjudication", "approved", ...). */
public record TimelineEntry(String claimId, Instant at, String type, String detail) {}
