package com.tmw.backend.matching.variance;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;

import java.math.BigDecimal;

public record ToleranceThreshold(
        BigDecimal qtyTolerancePct,
        BigDecimal priceTolerancePct,
        BigDecimal amountToleranceAbs,
        boolean requireGrn
) {
    public ToleranceThreshold {
        if (qtyTolerancePct == null || qtyTolerancePct.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "qtyTolerancePct không được null hoặc âm");
        }
        if (priceTolerancePct == null || priceTolerancePct.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "priceTolerancePct không được null hoặc âm");
        }
        if (amountToleranceAbs != null && amountToleranceAbs.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "amountToleranceAbs không được âm");
        }
    }
}