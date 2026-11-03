package com.kastellosth.appointme.dto;

import com.kastellosth.appointme.entity.Invoice;
import com.kastellosth.appointme.entity.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InvoiceResponse(
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
        Instant paidAt,
        Instant createdAt,
        List<InvoiceLineResponse> lines
) {
    public static InvoiceResponse from(Invoice i) {
        return new InvoiceResponse(i.getId(), i.getCustomerId(), i.getAppointmentId(), i.getNumber(),
                i.getStatus(), i.getCurrency(), i.getSubtotal(), i.getTaxTotal(), i.getTotal(),
                i.getIssuedAt(), i.getDueAt(), i.getPaidAt(), i.getCreatedAt(),
                i.getLines().stream().map(InvoiceLineResponse::from).toList());
    }
}
