package com.tmw.backend.allocation.api;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class AllocationCapacityExceededException extends BusinessException {

    private final CapacitySnapshot currentCapacity;
    private final BigDecimal requestedQty;
    private final BigDecimal requestedAmount;

    public AllocationCapacityExceededException(
            CapacitySnapshot currentCapacity,
            BigDecimal requestedQty,
            BigDecimal requestedAmount
    ) {
        super(
                ErrorCode.ALLOCATION_CAPACITY_EXCEEDED,
                String.format(
                        "Vượt hạn mức PO line %s (còn lại: qty=%s, amount=%s; yêu cầu: qty=%s, amount=%s)",
                        currentCapacity.poLineId(),
                        currentCapacity.remainingQty().toPlainString(),
                        currentCapacity.remainingAmount().toPlainString(),
                        requestedQty.toPlainString(),
                        requestedAmount.toPlainString()
                )
        );
        this.currentCapacity = currentCapacity;
        this.requestedQty = requestedQty;
        this.requestedAmount = requestedAmount;
    }
}