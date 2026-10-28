package com.claimflow.claims.domain;

import com.claimflow.events.ClaimStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ClaimRepository extends JpaRepository<Claim, String> {
    List<Claim> findTop100ByOrderBySubmittedAtDesc();

    List<Claim> findTop100ByMemberMemberNumberOrderBySubmittedAtDesc(String memberNumber);

    @Query("select c.status as status, count(c) as count, coalesce(sum(c.billedAmount), 0) as billed, "
            + "coalesce(sum(c.planPays), 0) as planPaid from Claim c group by c.status")
    List<StatusTotals> totalsByStatus();

    interface StatusTotals {
        ClaimStatus getStatus();
        long getCount();
        java.math.BigDecimal getBilled();
        java.math.BigDecimal getPlanPaid();
    }
}
