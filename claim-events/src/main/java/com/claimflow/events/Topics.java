package com.claimflow.events;

/** Kafka topic names. Both services import these, so a typo can't split the pipeline. */
public final class Topics {
    /** claims-service → adjudication-service: a member submitted a claim. */
    public static final String CLAIMS_SUBMITTED = "claims.submitted";
    /** adjudication-service → claims-service: the decision on a claim. */
    public static final String CLAIMS_ADJUDICATED = "claims.adjudicated";
    /** Messages that still failed after retries end up here for a person to look at. */
    public static final String CLAIMS_SUBMITTED_DLT = "claims.submitted.DLT";

    private Topics() {}
}
