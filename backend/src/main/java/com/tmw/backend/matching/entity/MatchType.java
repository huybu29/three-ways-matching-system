package com.tmw.backend.matching.entity;

/**
 * Kiểu đối chiếu của một dòng.
 * <ul>
 *   <li>THREE_WAY – Invoice ↔ PO ↔ GRN: số lượng so với số đã nhận kho.</li>
 *   <li>TWO_WAY   – Invoice ↔ PO (dịch vụ / policy require_grn = false):
 *                   số lượng so với số đặt hàng.</li>
 * </ul>
 */
public enum MatchType {
    TWO_WAY,
    THREE_WAY
}
