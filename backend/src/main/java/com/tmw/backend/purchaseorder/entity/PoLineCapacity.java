package com.tmw.backend.purchaseorder.entity;

import com.tmw.backend.allocation.api.AllocationCapacityExceededException;
import com.tmw.backend.allocation.api.CapacitySnapshot;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "po_line_capacity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PoLineCapacity {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    @Id
    @Column(name = "po_line_id", nullable = false)
    private UUID poLineId;

    @Column(name = "ordered_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal orderedQty = ZERO;

    @Column(name = "allocated_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedQty = ZERO;

    @Column(name = "reserved_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal reservedQty = ZERO;

    @Column(name = "received_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal receivedQty = ZERO;

    @Column(name = "ordered_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal orderedAmount = ZERO;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedAmount = ZERO;

    @Column(name = "reserved_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal reservedAmount = ZERO;

    @Column(name = "received_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal receivedAmount = ZERO;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public PoLineCapacity(UUID poLineId, BigDecimal orderedQty, BigDecimal orderedAmount) {
        if (poLineId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "po_line_id không được để trống");
        }
        this.poLineId = poLineId;
        this.orderedQty = normalizePositive(orderedQty, "ordered_qty");
        this.orderedAmount = normalizeNonNegative(orderedAmount, "ordered_amount");
        this.allocatedQty = ZERO;
        this.reservedQty = ZERO;
        this.receivedQty = ZERO;
        this.allocatedAmount = ZERO;
        this.reservedAmount = ZERO;
        this.receivedAmount = ZERO;
    }

    public BigDecimal getRemainingQty() {
        return orderedQty.subtract(allocatedQty).subtract(reservedQty);
    }

    public BigDecimal getRemainingAmount() {
        return orderedAmount.subtract(allocatedAmount).subtract(reservedAmount);
    }

    public CapacitySnapshot toSnapshot() {
        return new CapacitySnapshot(
                poLineId,
                orderedQty,
                allocatedQty,
                reservedQty,
                receivedQty,
                getRemainingQty(),
                orderedAmount,
                allocatedAmount,
                reservedAmount,
                receivedAmount,
                getRemainingAmount()
        );
    }

    public void reserve(BigDecimal qty, BigDecimal amount) {
        BigDecimal normQty = normalizePositive(qty, "qty");
        BigDecimal normAmount = normalizeNonNegative(amount, "amount");

        if (getRemainingQty().compareTo(normQty) < 0 || getRemainingAmount().compareTo(normAmount) < 0) {
            throw new AllocationCapacityExceededException(toSnapshot(), normQty, normAmount);
        }

        this.reservedQty = this.reservedQty.add(normQty);
        this.reservedAmount = this.reservedAmount.add(normAmount);
        assertInvariant();
    }

    public void confirm(BigDecimal qty, BigDecimal amount) {
        BigDecimal normQty = normalizePositive(qty, "qty");
        BigDecimal normAmount = normalizeNonNegative(amount, "amount");

        if (this.reservedQty.compareTo(normQty) < 0 || this.reservedAmount.compareTo(normAmount) < 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Số lượng hoặc giá trị xác nhận vượt quá phần đang giữ chỗ (reserved)"
            );
        }

        this.reservedQty = this.reservedQty.subtract(normQty);
        this.allocatedQty = this.allocatedQty.add(normQty);
        this.reservedAmount = this.reservedAmount.subtract(normAmount);
        this.allocatedAmount = this.allocatedAmount.add(normAmount);
        assertInvariant();
    }

    public void release(BigDecimal qty, BigDecimal amount) {
        BigDecimal normQty = normalizePositive(qty, "qty");
        BigDecimal normAmount = normalizeNonNegative(amount, "amount");

        if (this.reservedQty.compareTo(normQty) < 0 || this.reservedAmount.compareTo(normAmount) < 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Số lượng hoặc giá trị giải phóng vượt quá phần đang giữ chỗ (reserved)"
            );
        }

        this.reservedQty = this.reservedQty.subtract(normQty);
        this.reservedAmount = this.reservedAmount.subtract(normAmount);
        assertInvariant();
    }

    public void addReceived(BigDecimal qty, BigDecimal amount) {
        BigDecimal normQty = normalizePositive(qty, "received_qty");
        BigDecimal normAmount = normalizeNonNegative(amount, "received_amount");
        this.receivedQty = this.receivedQty.add(normQty);
        this.receivedAmount = this.receivedAmount.add(normAmount);
    }

    private void assertInvariant() {
        BigDecimal usedQty = allocatedQty.add(reservedQty);
        BigDecimal usedAmount = allocatedAmount.add(reservedAmount);

        if (allocatedQty.compareTo(ZERO) < 0
                || reservedQty.compareTo(ZERO) < 0
                || usedQty.compareTo(orderedQty) > 0
                || allocatedAmount.compareTo(ZERO) < 0
                || reservedAmount.compareTo(ZERO) < 0
                || usedAmount.compareTo(orderedAmount) > 0) {
            throw new AllocationCapacityExceededException(toSnapshot(), ZERO, ZERO);
        }
    }

    private static BigDecimal normalizePositive(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Trường " + fieldName + " phải lớn hơn 0"
            );
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizeNonNegative(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Trường " + fieldName + " không được âm"
            );
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}