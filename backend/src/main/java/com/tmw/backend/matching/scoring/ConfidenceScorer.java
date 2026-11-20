package com.tmw.backend.matching.scoring;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ConfidenceScorer {

    private static final int SCORE_SCALE = 4;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    private static final BigDecimal HALF = new BigDecimal("0.5000");
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ConfidenceWeightProperties weights;

    public ConfidenceBreakdown score(ConfidenceInput input) {
        if (input == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ConfidenceInput không được null");
        }

        BigDecimal poRefScore = scorePoReference(input);
        BigDecimal supplierScore = scoreSupplier(input);
        BigDecimal itemCodeScore = scoreItemCode(input);
        BigDecimal priceScore = scoreVariance(input.priceVariancePct(), input.priceTolerancePct());
        BigDecimal quantityScore = scoreVariance(input.qtyVariancePct(), input.qtyTolerancePct());

        BigDecimal weightedSum = poRefScore.multiply(weights.getPoReference())
                .add(supplierScore.multiply(weights.getSupplier()))
                .add(itemCodeScore.multiply(weights.getItemCode()))
                .add(priceScore.multiply(weights.getPrice()))
                .add(quantityScore.multiply(weights.getQuantity()))
                .setScale(SCORE_SCALE, RoundingMode.HALF_UP);

        BigDecimal clampedOverall = clamp0To1(weightedSum);

        return new ConfidenceBreakdown(
                poRefScore,
                supplierScore,
                itemCodeScore,
                priceScore,
                quantityScore,
                clampedOverall
        );
    }

    public BigDecimal aggregateOverall(List<ConfidenceBreakdown> lineScores) {
        if (lineScores == null || lineScores.isEmpty()) {
            return ZERO;
        }
        BigDecimal total = lineScores.stream()
                .map(ConfidenceBreakdown::overallScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return clamp0To1(total.divide(BigDecimal.valueOf(lineScores.size()), SCORE_SCALE, RoundingMode.HALF_UP));
    }

    private BigDecimal scorePoReference(ConfidenceInput input) {
        // Khớp trực tiếp cả PO line hoặc header PO trên hóa đơn -> 1.0
        if (input.invoiceLinePoLineIdHint() != null
                && input.invoiceLinePoLineIdHint().equals(input.candidatePoLineId())) {
            return ONE;
        }
        if (input.invoicePoIdReference() != null
                && input.invoicePoIdReference().equals(input.candidatePoId())) {
            return ONE;
        }
        // Hóa đơn không ghi sẵn số PO (tự dò theo nhà cung cấp) -> 0.5
        if (input.invoicePoIdReference() == null && input.invoiceLinePoLineIdHint() == null) {
            return HALF;
        }
        return ZERO;
    }

    private BigDecimal scoreSupplier(ConfidenceInput input) {
        if (input.invoiceSupplierId() != null && input.invoiceSupplierId().equals(input.poSupplierId())) {
            return ONE;
        }
        return ZERO;
    }

    private BigDecimal scoreItemCode(ConfidenceInput input) {
        if (input.invoiceItemCode() == null || input.poItemCode() == null) {
            return ZERO;
        }
        String invCode = input.invoiceItemCode().trim().toUpperCase(Locale.ROOT);
        String poCode = input.poItemCode().trim().toUpperCase(Locale.ROOT);

        if (invCode.isEmpty() || poCode.isEmpty()) {
            return ZERO;
        }
        if (invCode.equals(poCode)) {
            return ONE;
        }
        // Khớp một phần (ví dụ mã chi nhánh/hậu tố đóng gói) -> 0.5
        if (invCode.contains(poCode) || poCode.contains(invCode)) {
            return HALF;
        }
        return ZERO;
    }

    /**
     * Chấm điểm cho tiêu chí giá và số lượng:
     * - Lệch = 0%: 1.0000
     * - Lệch trong ngưỡng (0% .. tolerance%]: giảm tuyến tính từ 1.0000 xuống 0.7000
     * - Lệch ngoài ngưỡng: giảm tuyến tính từ 0.7000 về 0.0000 khi đạt 100%
     */
    private BigDecimal scoreVariance(BigDecimal variancePct, BigDecimal tolerancePct) {
        if (variancePct == null) {
            return ZERO;
        }
        BigDecimal absVar = variancePct.abs();
        if (absVar.signum() == 0) {
            return ONE;
        }

        BigDecimal tol = (tolerancePct != null && tolerancePct.signum() > 0) ? tolerancePct : BigDecimal.ZERO;

        if (tol.signum() > 0 && absVar.compareTo(tol) <= 0) {
            BigDecimal ratio = absVar.divide(tol, 8, RoundingMode.HALF_UP);
            BigDecimal penalty = new BigDecimal("0.3000").multiply(ratio);
            return clamp0To1(ONE.subtract(penalty).setScale(SCORE_SCALE, RoundingMode.HALF_UP));
        }

        if (absVar.compareTo(HUNDRED) >= 0) {
            return ZERO;
        }

        BigDecimal remainingSpan = HUNDRED.subtract(tol);
        if (remainingSpan.signum() <= 0) {
            return ZERO;
        }
        BigDecimal excess = absVar.subtract(tol);
        BigDecimal decayRatio = excess.divide(remainingSpan, 8, RoundingMode.HALF_UP);
        BigDecimal score = new BigDecimal("0.7000").multiply(BigDecimal.ONE.subtract(decayRatio));
        return clamp0To1(score.setScale(SCORE_SCALE, RoundingMode.HALF_UP));
    }

    private static BigDecimal clamp0To1(BigDecimal value) {
        if (value.compareTo(ZERO) < 0) {
            return ZERO;
        }
        if (value.compareTo(ONE) > 0) {
            return ONE;
        }
        return value;
    }
}