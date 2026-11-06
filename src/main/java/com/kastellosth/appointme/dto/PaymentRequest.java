package com.kastellosth.appointme.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.kastellosth.appointme.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PaymentRequest(
        @Schema(example = "25.00") @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @Schema(example = "CARD") @NotNull PaymentMethod method,
        @Schema(example = "POS-1042") @Size(max = 100) String reference
) {
}
