package com.tmw.backend.purchaseorder.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "po_line_capacities")
public class PoLineCapacity {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    private PurchaseOrderLine poLine;

    @Column(name = "ordered_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal orderedQty;

    @Column(name = "allocated_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedQty = BigDecimal.ZERO.setScale(4);

    @Column(name = "reserved_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal reservedQty = BigDecimal.ZERO.setScale(4);

    @Column(name = "received_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal receivedQty = BigDecimal.ZERO.setScale(4);

    @Column(name = "ordered_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal orderedAmount;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedAmount = BigDecimal.ZERO.setScale(4);

    @Column(name = "reserved_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal reservedAmount = BigDecimal.ZERO.setScale(4);

    @Version
    @Column(name = "version")
    private Long version;

    public PoLineCapacity() {}

    public PoLineCapacity(PurchaseOrderLine poLine, BigDecimal orderedQty, BigDecimal orderedAmount) {
        this.poLine = poLine;
        this.orderedQty = orderedQty;
        this.orderedAmount = orderedAmount;
        this.allocatedQty = BigDecimal.ZERO.setScale(4);
        this.reservedQty = BigDecimal.ZERO.setScale(4);
        this.receivedQty = BigDecimal.ZERO.setScale(4);
        this.allocatedAmount = BigDecimal.ZERO.setScale(4);
        this.reservedAmount = BigDecimal.ZERO.setScale(4);
    }

    public BigDecimal getRemainingQty() {
        return orderedQty.subtract(allocatedQty).subtract(reservedQty);
    }

    public BigDecimal getRemainingAmount() {
        return orderedAmount.subtract(allocatedAmount).subtract(reservedAmount);
    }

    public void reserve(BigDecimal qty, BigDecimal amount) {
        if (getRemainingQty().compareTo(qty) < 0 || getRemainingAmount().compareTo(amount) < 0) {
            throw new IllegalStateException("ALLOCATION_CAPACITY_EXCEEDED: Vượt quá hạn mức còn lại");
        }
        this.reservedQty = this.reservedQty.add(qty);
        this.reservedAmount = this.reservedAmount.add(amount);
    }

    public void confirm(BigDecimal qty, BigDecimal amount) {
        if (this.reservedQty.compareTo(qty) < 0 || this.reservedAmount.compareTo(amount) < 0) {
            throw new IllegalStateException("Không đủ số lượng/số tiền đang giữ chỗ để xác nhận");
        }
        this.reservedQty = this.reservedQty.subtract(qty);
        this.allocatedQty = this.allocatedQty.add(qty);
        this.reservedAmount = this.reservedAmount.subtract(amount);
        this.allocatedAmount = this.allocatedAmount.add(amount);
    }

    public void release(BigDecimal qty, BigDecimal amount) {
        if (this.reservedQty.compareTo(qty) < 0 || this.reservedAmount.compareTo(amount) < 0) {
            throw new IllegalStateException("Không thể hoàn lại vượt quá số lượng/số tiền đang giữ chỗ");
        }
        this.reservedQty = this.reservedQty.subtract(qty);
        this.reservedAmount = this.reservedAmount.subtract(amount);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public PurchaseOrderLine getPoLine() { return poLine; }
    public void setPoLine(PurchaseOrderLine poLine) { this.poLine = poLine; }
    public BigDecimal getOrderedQty() { return orderedQty; }
    public void setOrderedQty(BigDecimal orderedQty) { this.orderedQty = orderedQty; }
    public BigDecimal getAllocatedQty() { return allocatedQty; }
    public BigDecimal getReservedQty() { return reservedQty; }
    public BigDecimal getReceivedQty() { return receivedQty; }
    public void setReceivedQty(BigDecimal receivedQty) { this.receivedQty = receivedQty; }
    public BigDecimal getOrderedAmount() { return orderedAmount; }
    public void setOrderedAmount(BigDecimal orderedAmount) { this.orderedAmount = orderedAmount; }
    public BigDecimal getAllocatedAmount() { return allocatedAmount; }
    public BigDecimal getReservedAmount() { return reservedAmount; }
    public Long getVersion() { return version; }
}