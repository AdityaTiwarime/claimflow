package com.claimflow.claims.timeline;

import java.util.List;

/**
 * A claim's audit trail. Stored in MongoDB: it's an append-only list of loosely structured events,
 * read back per claim, which suits a document store better than a relational table.
 */
public interface ClaimTimeline {
    void record(TimelineEntry entry);

    List<TimelineEntry> forClaim(String claimId);
}
