package com.tmw.backend.matching.scoring;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "tmw.matching.confidence.weights")
public class ConfidenceWeightProperties {

    private BigDecimal poReference = new BigDecimal("0.2500");
    private BigDecimal supplier = new BigDecimal("0.2500");
    private BigDecimal itemCode = new BigDecimal("0.2000");
    private BigDecimal price = new BigDecimal("0.1500");
    private BigDecimal quantity = new BigDecimal("0.1500");

    @PostConstruct
    public void validateWeights() {
        requireNonNegative(poReference, "po-reference");
        requireNonNegative(supplier, "supplier");
        requireNonNegative(itemCode, "item-code");
        requireNonNegative(price, "price");
        requireNonNegative(quantity, "quantity");

        BigDecimal sum = poReference
                .add(supplier)
                .add(itemCode)
                .add(price)
                .add(quantity)
                .setScale(4, RoundingMode.HALF_UP);

        if (sum.compareTo(BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP)) != 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Tổng 5 trọng số ConfidenceScorer phải bằng 1.0000, hiện tại là: " + sum.toPlainString()
            );
        }
    }

    private static void requireNonNegative(BigDecimal weight, String name) {
        if (weight == null || weight.signum() < 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Trọng số cấu hình '" + name + "' không được null hoặc âm"
            );
        }
    }
}