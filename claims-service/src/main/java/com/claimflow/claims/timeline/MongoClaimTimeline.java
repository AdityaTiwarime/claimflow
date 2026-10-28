package com.claimflow.claims.timeline;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/** Each timeline entry is one document in the claim_events collection, indexed by claim and time. */
@Component
@ConditionalOnProperty(name = "claimflow.timeline", havingValue = "mongo", matchIfMissing = true)
public class MongoClaimTimeline implements ClaimTimeline {

    static final String COLLECTION = "claim_events";

    private final MongoTemplate mongo;
    private volatile boolean indexed;

    public MongoClaimTimeline(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void record(TimelineEntry entry) {
        ensureIndex();
        mongo.insert(entry, COLLECTION);
    }

    @Override
    public List<TimelineEntry> forClaim(String claimId) {
        Query q = Query.query(Criteria.where("claimId").is(claimId)).with(Sort.by("at"));
        return mongo.find(q, TimelineEntry.class, COLLECTION);
    }

    private void ensureIndex() {
        if (!indexed) {
            mongo.indexOps(COLLECTION).ensureIndex(new Index().on("claimId", Sort.Direction.ASC).on("at", Sort.Direction.ASC));
            indexed = true;
        }
    }
}
