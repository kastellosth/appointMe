package com.kastellosth.appointme.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record TenantBillingRequest(
        @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4) BigDecimal taxRate,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter currency code such as EUR") String currency
) {
}
