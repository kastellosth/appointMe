package com.kastellosth.appointme.dto;

import com.kastellosth.appointme.entity.InvoiceStatus;

import java.math.BigDecimal;
import java.util.List;

public record InvoicePaymentsResponse(
        Long invoiceId,
        InvoiceStatus status,
        BigDecimal total,
        BigDecimal paid,
        BigDecimal balance,
        List<PaymentResponse> payments
) {
}
