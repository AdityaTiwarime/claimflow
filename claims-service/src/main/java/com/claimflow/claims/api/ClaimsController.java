package com.claimflow.claims.api;

import com.claimflow.claims.ClaimService;
import com.claimflow.claims.api.Views.ClaimView;
import com.claimflow.claims.api.Views.MemberView;
import com.claimflow.claims.api.Views.PlanView;
import com.claimflow.claims.api.Views.StatusSummary;
import com.claimflow.claims.domain.Claim;
import com.claimflow.claims.domain.ClaimRepository;
import com.claimflow.claims.domain.MemberRepository;
import com.claimflow.claims.domain.PlanRepository;
import com.claimflow.claims.timeline.ClaimTimeline;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ClaimsController {

    private final ClaimService service;
    private final ClaimRepository claims;
    private final MemberRepository members;
    private final PlanRepository plans;
    private final ClaimTimeline timeline;

    public ClaimsController(ClaimService service, ClaimRepository claims, MemberRepository members,
                            PlanRepository plans, ClaimTimeline timeline) {
        this.service = service;
        this.claims = claims;
        this.members = members;
        this.plans = plans;
        this.timeline = timeline;
    }

    @GetMapping("/plans")
    public List<PlanView> plans() {
        return plans.findAll().stream().map(PlanView::of).toList();
    }

    @GetMapping("/members")
    public List<MemberView> members() {
        return members.findAll().stream().map(MemberView::of).toList();
    }

    @GetMapping("/members/{memberNumber}")
    public MemberView member(@PathVariable String memberNumber) {
        return members.findByMemberNumber(memberNumber).map(MemberView::of)
                .orElseThrow(() -> new NotFoundException("No member " + memberNumber));
    }

    /** 202 Accepted: the claim is saved, and the decision arrives asynchronously via Kafka. */
    @PostMapping("/claims")
    public ResponseEntity<ClaimView> submit(@Valid @RequestBody SubmitClaimRequest req) {
        Claim c = service.submit(req);
        return ResponseEntity.accepted().location(URI.create("/api/claims/" + c.getId())).body(ClaimView.of(c, List.of()));
    }

    @GetMapping("/claims")
    @Transactional(readOnly = true)
    public List<ClaimView> claims(@RequestParam(required = false) String memberNumber) {
        List<Claim> found = memberNumber == null || memberNumber.isBlank()
                ? claims.findTop100ByOrderBySubmittedAtDesc()
                : claims.findTop100ByMemberMemberNumberOrderBySubmittedAtDesc(memberNumber);
        return found.stream().map(c -> ClaimView.of(c, List.of())).toList();
    }

    @GetMapping("/claims/{id}")
    @Transactional(readOnly = true)
    public ClaimView claim(@PathVariable String id) {
        Claim c = claims.findById(id).orElseThrow(() -> new NotFoundException("No claim " + id));
        return ClaimView.of(c, timeline.forClaim(id));
    }

    @GetMapping("/summary")
    public List<StatusSummary> summary() {
        return claims.totalsByStatus().stream()
                .map(t -> new StatusSummary(t.getStatus(), t.getCount(), t.getBilled(), t.getPlanPaid()))
                .toList();
    }
}
