package com.kastellosth.appointme.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InvoiceRequest(
        @NotNull Long customerId,
        Long appointmentId,
        @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter currency code such as EUR") String currency,
        @Size(max = 100) List<@NotNull @Valid InvoiceLineRequest> lines
) {
}
