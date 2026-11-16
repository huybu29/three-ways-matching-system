package com.tmw.backend.matching.entity;

import java.math.RoundingMode;

/**
 * Quy ước scale cho các cột thập phân trong module Matching.
 * Phải khớp với numeric(p, s) trong migration và với Validator Money/Quantity của BE-1.
 */
public final class DecimalScales {

    private DecimalScales() { }

    /** Money: numeric(19,4) – đủ cho VND (scale 0) lẫn ngoại tệ. */
    public static final int MONEY_SCALE = 4;

    /** Quantity: numeric(19,4). */
    public static final int QTY_SCALE = 4;

    /** Tỷ lệ phần trăm: numeric(9,4), vd 2.5000 = 2.5%. */
    public static final int PCT_SCALE = 4;

    /** Confidence: numeric(5,4) trong [0, 1]. */
    public static final int CONFIDENCE_SCALE = 4;

    /** Làm tròn kiểu kế toán (round half up). */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
}
