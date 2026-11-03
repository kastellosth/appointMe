package com.kastellosth.appointme.dto;

import com.kastellosth.appointme.entity.InvoiceLine;

import java.math.BigDecimal;

public record InvoiceLineResponse(
        int lineNo,
        String description,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal taxRate,
        BigDecimal lineTotal,
        BigDecimal tax
) {
    public static InvoiceLineResponse from(InvoiceLine l) {
        return new InvoiceLineResponse(l.getLineNo(), l.getDescription(), l.getQuantity(),
                l.getUnitPrice(), l.getTaxRate(), l.getLineTotal(), l.getTaxAmount());
    }
}
