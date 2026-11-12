package com.tmw.backend.matching.entity;

/**
 * Trạng thái hạn mức của một dòng đối chiếu, đồng bộ với Capacity của BE-3.
 * <ul>
 *   <li>RESERVED  – đang giữ chỗ (proposal PROPOSED/APPROVED)</li>
 *   <li>ALLOCATED – đã chốt phân bổ khi POST (commitAllocation)</li>
 *   <li>RELEASED  – đã hoàn lại hạn mức (proposal REJECTED/CANCELLED)</li>
 * </ul>
 */
public enum MatchLineStatus {
    RESERVED,
    ALLOCATED,
    RELEASED
}
