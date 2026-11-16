package com.tmw.backend.purchaseorder.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_lines")
public class PurchaseOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(name = "description")
    private String description;

    @Column(name = "ordered_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal orderedQty;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal vatRate;

    @OneToOne(mappedBy = "poLine", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private PoLineCapacity capacity;

    public PurchaseOrderLine() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public void setPurchaseOrder(PurchaseOrder purchaseOrder) { this.purchaseOrder = purchaseOrder; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getOrderedQty() { return orderedQty; }
    public void setOrderedQty(BigDecimal orderedQty) { this.orderedQty = orderedQty; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getVatRate() { return vatRate; }
    public void setVatRate(BigDecimal vatRate) { this.vatRate = vatRate; }
    public PoLineCapacity getCapacity() { return capacity; }
    public void setCapacity(PoLineCapacity capacity) {
        this.capacity = capacity;
        if (capacity != null) {
            capacity.setPoLine(this);
        }
    }
}