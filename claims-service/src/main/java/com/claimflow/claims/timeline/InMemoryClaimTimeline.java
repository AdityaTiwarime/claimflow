package com.claimflow.claims.timeline;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** For tests and running without MongoDB: claimflow.timeline=memory. */
@Component
@ConditionalOnProperty(name = "claimflow.timeline", havingValue = "memory")
public class InMemoryClaimTimeline implements ClaimTimeline {

    private final List<TimelineEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void record(TimelineEntry entry) {
        entries.add(entry);
    }

    @Override
    public List<TimelineEntry> forClaim(String claimId) {
        return entries.stream()
                .filter(e -> e.claimId().equals(claimId))
                .sorted(Comparator.comparing(TimelineEntry::at))
                .toList();
    }
}
