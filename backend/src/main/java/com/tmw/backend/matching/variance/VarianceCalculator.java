package com.tmw.backend.matching.variance;

import com.tmw.backend.common.enums.CurrencyCode;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static com.tmw.backend.common.decimal.DecimalScales.*;

@Component
public class VarianceCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int INTERMEDIATE_SCALE = 8;

 
    public VarianceResult calculate(
            BigDecimal matchedQty,
            BigDecimal referenceQty,
            BigDecimal invoiceUnitPrice,
            BigDecimal poUnitPrice,
            CurrencyCode invoiceCurrency,
            CurrencyCode poCurrency
    ) {
        validateInputs(matchedQty, referenceQty, invoiceUnitPrice, poUnitPrice, invoiceCurrency, poCurrency);

        BigDecimal normMatchedQty = matchedQty.setScale(QTY_SCALE, ROUNDING);
        BigDecimal normRefQty = referenceQty.setScale(QTY_SCALE, ROUNDING);
        BigDecimal normInvPrice = invoiceUnitPrice.setScale(MONEY_SCALE, ROUNDING);
        BigDecimal normPoPrice = poUnitPrice.setScale(MONEY_SCALE, ROUNDING);

        // 1. Chênh lệch số lượng (dương = hóa đơn nhiều hơn tham chiếu, âm = hóa đơn ít hơn tham chiếu)
        BigDecimal qtyVariance = normMatchedQty.subtract(normRefQty).setScale(QTY_SCALE, ROUNDING);
        BigDecimal qtyVariancePct = qtyVariance.abs()
                .divide(normRefQty, INTERMEDIATE_SCALE, ROUNDING)
                .multiply(HUNDRED)
                .setScale(PCT_SCALE, ROUNDING);

        // 2. Chênh lệch đơn giá
        BigDecimal priceVariance = normInvPrice.subtract(normPoPrice).setScale(MONEY_SCALE, ROUNDING);
        BigDecimal priceVariancePct;
        if (normPoPrice.signum() == 0) {
            priceVariancePct = normInvPrice.signum() == 0
                    ? BigDecimal.ZERO.setScale(PCT_SCALE, ROUNDING)
                    : HUNDRED.setScale(PCT_SCALE, ROUNDING);
        } else {
            priceVariancePct = priceVariance.abs()
                    .divide(normPoPrice, INTERMEDIATE_SCALE, ROUNDING)
                    .multiply(HUNDRED)
                    .setScale(PCT_SCALE, ROUNDING);
        }

        // 3. Chênh lệch thành tiền thực tế so với giá trị tham chiếu chuẩn
        BigDecimal actualInvoiceAmount = normMatchedQty.multiply(normInvPrice);
        BigDecimal expectedReferenceAmount = normRefQty.multiply(normPoPrice);
        BigDecimal amountVariance = actualInvoiceAmount.subtract(expectedReferenceAmount)
                .setScale(MONEY_SCALE, ROUNDING);

        return new VarianceResult(
                normRefQty,
                qtyVariance,
                qtyVariancePct,
                priceVariance,
                priceVariancePct,
                amountVariance
        );
    }

    /**
     * Đánh giá kết quả chênh lệch có nằm trong ngưỡng cho phép của TolerancePolicy hay không.
     */
    public boolean isWithinTolerance(VarianceResult result, ToleranceThreshold threshold) {
        if (result == null || threshold == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "VarianceResult và ToleranceThreshold không được null");
        }

        boolean qtyOk = result.qtyVariancePct().compareTo(threshold.qtyTolerancePct()) <= 0;
        boolean priceOk = result.priceVariancePct().compareTo(threshold.priceTolerancePct()) <= 0;
        boolean amountOk = threshold.amountToleranceAbs() == null
                || result.amountVariance().abs().compareTo(threshold.amountToleranceAbs()) <= 0;

        return qtyOk && priceOk && amountOk;
    }

    private void validateInputs(
            BigDecimal matchedQty,
            BigDecimal referenceQty,
            BigDecimal invoiceUnitPrice,
            BigDecimal poUnitPrice,
            CurrencyCode invoiceCurrency,
            CurrencyCode poCurrency
    ) {
        if (matchedQty == null || matchedQty.signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số lượng hóa đơn đối chiếu (matchedQty) phải lớn hơn 0");
        }
        if (referenceQty == null || referenceQty.signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số lượng tham chiếu (referenceQty) phải lớn hơn 0");
        }
        if (invoiceUnitPrice == null || invoiceUnitPrice.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Đơn giá hóa đơn (invoiceUnitPrice) không được null hoặc âm");
        }
        if (poUnitPrice == null || poUnitPrice.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Đơn giá PO (poUnitPrice) không được null hoặc âm");
        }
        if (invoiceCurrency != null && poCurrency != null && invoiceCurrency != poCurrency) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    String.format("Không thể đối chiếu lệch loại tiền tệ: hóa đơn (%s) và PO (%s)", invoiceCurrency, poCurrency)
            );
        }
    }
}