package com.kastellosth.appointme.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The complete new list of lines for a DRAFT invoice (it replaces the old list). */
public record InvoiceLinesRequest(
        @NotNull @Size(max = 100) List<@NotNull @Valid InvoiceLineRequest> lines
) {
}
