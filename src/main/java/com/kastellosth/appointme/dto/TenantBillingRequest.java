package com.kastellosth.appointme.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/** Both fields are required: PUT replaces the billing settings as a whole. */
public record TenantBillingRequest(
        /** A fraction: 0.24 = 24 %. At most 4 decimals. */
        @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4) BigDecimal taxRate,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter currency code such as EUR") String currency
) {
}
