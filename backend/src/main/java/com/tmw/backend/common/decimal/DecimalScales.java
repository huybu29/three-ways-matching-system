package com.tmw.backend.common.decimal;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DecimalScales {

    public static final int DEFAULT_PRECISION = 19;

    public static final int QTY_SCALE = 4;
    public static final int MONEY_SCALE = 4;
    public static final int PCT_SCALE = 4;
    public static final int SCORE_SCALE = 4;
    public static final int INTERMEDIATE_SCALE = 8;

    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final BigDecimal ZERO_QTY = BigDecimal.ZERO.setScale(QTY_SCALE, ROUNDING);
    public static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
    public static final BigDecimal ZERO_PCT = BigDecimal.ZERO.setScale(PCT_SCALE, ROUNDING);
    public static final BigDecimal ZERO_SCORE = BigDecimal.ZERO.setScale(SCORE_SCALE, ROUNDING);

    private DecimalScales() {
    }

    public static BigDecimal scaleQty(BigDecimal value) {
        return value == null ? null : value.setScale(QTY_SCALE, ROUNDING);
    }

    public static BigDecimal scaleMoney(BigDecimal value) {
        return value == null ? null : value.setScale(MONEY_SCALE, ROUNDING);
    }

    public static BigDecimal scalePct(BigDecimal value) {
        return value == null ? null : value.setScale(PCT_SCALE, ROUNDING);
    }

    public static BigDecimal scaleScore(BigDecimal value) {
        return value == null ? null : value.setScale(SCORE_SCALE, ROUNDING);
    }
}