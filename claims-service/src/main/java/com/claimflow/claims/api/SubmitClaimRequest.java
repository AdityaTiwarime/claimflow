package com.claimflow.claims.api;

import com.claimflow.events.ServiceType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** The JSON body of POST /api/claims. Invalid input is rejected with 400 before anything is saved. */
public record SubmitClaimRequest(
        @NotBlank String memberNumber,
        @NotNull ServiceType serviceType,
        @NotNull @PastOrPresent LocalDate serviceDate,
        @NotBlank @Size(max = 120) String providerName,
        @NotNull @DecimalMin("0.01") @DecimalMax("1000000.00") @Digits(integer = 10, fraction = 2) BigDecimal billedAmount) {}
