package com.claimflow.events;

public enum ClaimStatus {
    /** Received, waiting for the adjudication service. */
    SUBMITTED,
    APPROVED,
    DENIED,
    /** Needs a person to review it (for example, a very large claim). */
    PENDED;

    public boolean isFinal() {
        return this == APPROVED || this == DENIED;
    }
}
