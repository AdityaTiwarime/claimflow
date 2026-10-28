package com.claimflow.adjudication;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimflow.events.ClaimAdjudicated;
import com.claimflow.events.ClaimStatus;
import com.claimflow.events.ClaimSubmitted;
import com.claimflow.events.ServiceType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdjudicationEngineTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);
    private final AdjudicationEngine engine = new AdjudicationEngine(new BigDecimal("50000"), CLOCK);

    /** A member on a plan with a 500 deductible (300 met), 20% coinsurance and a 10,000 annual max. */
    private static ClaimSubmitted claim(String billed) {
        return claim(billed, ServiceType.PRIMARY_CARE, LocalDate.of(2026, 2, 15), "300", "0");
    }

    private static ClaimSubmitted claim(String billed, ServiceType type, LocalDate serviceDate, String deductibleMet, String benefitsUsed) {
        return new ClaimSubmitted(
                "C-1", "M-1001", type, serviceDate, new BigDecimal(billed),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                "GOLD", EnumSet.of(ServiceType.PRIMARY_CARE, ServiceType.LAB),
                new BigDecimal("500"), new BigDecimal(deductibleMet), 20,
                new BigDecimal("10000"), new BigDecimal(benefitsUsed));
    }

    private static BigDecimal m(String v) {
        return new BigDecimal(v).setScale(2);
    }

    @Test
    @DisplayName("remaining deductible first, then 20% coinsurance on the rest")
    void deductibleThenCoinsurance() {
        ClaimAdjudicated d = engine.adjudicate(claim("1000"));
        // 200 deductible left; 800 remains; member pays 20% of 800 = 160.
        assertThat(d.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(d.deductibleApplied()).isEqualTo(m("200"));
        assertThat(d.planPays()).isEqualTo(m("640"));
        assertThat(d.memberPays()).isEqualTo(m("360"));
        assertThat(d.reasonCodes()).containsExactly("DEDUCTIBLE_APPLIED");
        assertThat(d.decidedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void deductibleAlreadyMetOnlyCoinsuranceApplies() {
        ClaimAdjudicated d = engine.adjudicate(claim("100", ServiceType.LAB, LocalDate.of(2026, 5, 1), "500", "0"));
        assertThat(d.planPays()).isEqualTo(m("80"));
        assertThat(d.memberPays()).isEqualTo(m("20"));
        assertThat(d.deductibleApplied()).isEqualTo(m("0"));
        assertThat(d.reasonCodes()).isEmpty();
    }

    @Test
    void smallClaimEntirelyInsideTheDeductibleIsPaidByTheMember() {
        ClaimAdjudicated d = engine.adjudicate(claim("150"));
        assertThat(d.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(d.planPays()).isEqualTo(m("0"));
        assertThat(d.memberPays()).isEqualTo(m("150"));
    }

    @Test
    void serviceBeforeCoverageStartsIsDenied() {
        ClaimAdjudicated d = engine.adjudicate(claim("100", ServiceType.LAB, LocalDate.of(2025, 12, 31), "0", "0"));
        assertThat(d.status()).isEqualTo(ClaimStatus.DENIED);
        assertThat(d.reasonCodes()).containsExactly("COVERAGE_INACTIVE");
        assertThat(d.planPays()).isEqualTo(m("0"));
    }

    @Test
    void serviceAfterCoverageEndsIsDenied() {
        ClaimAdjudicated d = engine.adjudicate(claim("100", ServiceType.LAB, LocalDate.of(2027, 1, 1), "0", "0"));
        assertThat(d.status()).isEqualTo(ClaimStatus.DENIED);
    }

    @Test
    void serviceTheplanDoesNotCoverIsDenied() {
        ClaimAdjudicated d = engine.adjudicate(claim("100", ServiceType.IMAGING, LocalDate.of(2026, 5, 1), "0", "0"));
        assertThat(d.status()).isEqualTo(ClaimStatus.DENIED);
        assertThat(d.reasonCodes()).containsExactly("SERVICE_NOT_COVERED");
    }

    @Test
    void openEndedCoverageIsActive() {
        ClaimSubmitted c = claim("100");
        ClaimSubmitted openEnded = new ClaimSubmitted(c.claimId(), c.memberNumber(), c.serviceType(), LocalDate.of(2030, 1, 1),
                c.billedAmount(), c.coverageStart(), null, c.planCode(), c.coveredServices(), c.deductible(),
                c.deductibleMet(), c.coinsurancePercent(), c.annualMax(), c.benefitsUsed());
        assertThat(engine.adjudicate(openEnded).status()).isEqualTo(ClaimStatus.APPROVED);
    }

    @ParameterizedTest(name = "billed {0} -> {1}")
    @CsvSource({"49999.99, APPROVED", "50000, PENDED", "120000, PENDED"})
    void largeClaimsArePendedForManualReview(String billed, ClaimStatus expected) {
        assertThat(engine.adjudicate(claim(billed)).status()).isEqualTo(expected);
    }

    @Test
    void planPaymentIsCappedAtTheRemainingAnnualMaximum() {
        // Deductible met, 9,900 of 10,000 already used: plan can pay only 100 more.
        ClaimAdjudicated d = engine.adjudicate(claim("1000", ServiceType.LAB, LocalDate.of(2026, 6, 1), "500", "9900"));
        assertThat(d.planPays()).isEqualTo(m("100"));
        assertThat(d.memberPays()).isEqualTo(m("900"));
        assertThat(d.reasonCodes()).containsExactly("ANNUAL_MAX_REACHED");
    }

    @ParameterizedTest(name = "billed {0}: plan + member = billed")
    @CsvSource({"0.01", "33.33", "199.99", "777.77", "1234.56", "9999.99"})
    void amountsAlwaysAddUpToTheBill(String billed) {
        ClaimAdjudicated d = engine.adjudicate(claim(billed));
        assertThat(d.planPays().add(d.memberPays())).isEqualByComparingTo(billed);
        assertThat(d.planPays().signum()).isGreaterThanOrEqualTo(0);
        assertThat(d.memberPays().signum()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void coveredServicesAreRespectedForEveryType() {
        Set<ServiceType> covered = EnumSet.of(ServiceType.PRIMARY_CARE, ServiceType.LAB);
        for (ServiceType t : ServiceType.values()) {
            ClaimStatus s = engine.adjudicate(claim("100", t, LocalDate.of(2026, 5, 1), "500", "0")).status();
            assertThat(s).as(t.name()).isEqualTo(covered.contains(t) ? ClaimStatus.APPROVED : ClaimStatus.DENIED);
        }
    }
}
