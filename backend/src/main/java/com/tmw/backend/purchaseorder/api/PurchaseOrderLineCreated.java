package com.tmw.backend.purchaseorder.api;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseOrderLineCreated(
        UUID purchaseOrderId,
        UUID poLineId,
        BigDecimal orderedQty,
        BigDecimal orderedAmount
) {
    public PurchaseOrderLineCreated {
        if (purchaseOrderId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "purchaseOrderId không được null");
        }
        if (poLineId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "poLineId không được null");
        }
        if (orderedQty == null || orderedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "orderedQty phải lớn hơn 0");
        }
        if (orderedAmount == null || orderedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "orderedAmount không được âm");
        }
    }
}