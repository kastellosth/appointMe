package com.kastellosth.appointme.entity;

import com.kastellosth.appointme.support.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "invoice_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /** A fraction: 0.2400 means 24 %. */
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal taxRate;

    /** quantity x unitPrice. Exact: a whole number times a 2-decimal price needs no rounding. */
    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    /**
     * The tax of this line, calculated ONCE (when the line is made) and stored.
     * Reading an old invoice never recalculates it, so a future change of the rounding rule
     * cannot change what an issued invoice says.
     */
    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount;

    /**
     * A line that is not attached to an invoice yet. Invoice.replaceLines() attaches it.
     * taxRate must not be null: the service decides the default (the business's rate).
     */
    public static InvoiceLine of(String description, int quantity, BigDecimal unitPrice, BigDecimal taxRate) {
        InvoiceLine line = new InvoiceLine();
        line.description = description;
        line.quantity = quantity;
        line.unitPrice = Money.exactCents(unitPrice);
        line.taxRate = Money.exactRate(taxRate);
        line.lineTotal = line.unitPrice.multiply(BigDecimal.valueOf(quantity));
        line.taxAmount = Money.taxOf(line.lineTotal, line.taxRate);
        return line;
    }

    /** Called only by Invoice. Package-private on purpose: nobody else may move a line. */
    void attachTo(Invoice invoice, int lineNo) {
        this.invoice = invoice;
        this.tenantId = invoice.getTenantId();
        this.lineNo = lineNo;
    }
}
