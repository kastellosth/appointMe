package com.kastellosth.appointme.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record InvoiceLineRequest(
        @NotBlank @Size(max = 255) String description,
        @NotNull @Min(1) @Max(10000) Integer quantity,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal unitPrice,
        /** A fraction, 0.2400 = 24 %. Optional, defaults to the business's tax rate (Day 21). */
        @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4) BigDecimal taxRate
) {
}
