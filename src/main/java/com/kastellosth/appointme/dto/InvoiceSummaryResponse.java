package com.kastellosth.appointme.dto;

import com.kastellosth.appointme.entity.Invoice;
import com.kastellosth.appointme.entity.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceSummaryResponse(
        Long id,
        Long customerId,
        Long appointmentId,
        String number,
        InvoiceStatus status,
        String currency,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal total,
        Instant issuedAt,
        Instant dueAt,
        Instant createdAt
) {
    public static InvoiceSummaryResponse from(Invoice i) {
        return new InvoiceSummaryResponse(i.getId(), i.getCustomerId(), i.getAppointmentId(), i.getNumber(),
                i.getStatus(), i.getCurrency(), i.getSubtotal(), i.getTaxTotal(), i.getTotal(),
                i.getIssuedAt(), i.getDueAt(), i.getCreatedAt());
    }
}
