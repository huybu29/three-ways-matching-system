package com.tmw.backend.matching.candidate;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.purchaseorder.api.PoLineCandidateQuery;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Cấu hình CandidateFinder (tmw.matching.candidate.*).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "tmw.matching.candidate")
public class CandidateFinderProperties {

    /** Số ứng viên tối đa giữ lại cho mỗi dòng hóa đơn. */
    private int maxPerLine = 5;

    /** Trần số dòng PO đọc từ DB cho một hóa đơn (chống truy vấn quá lớn). */
    private int maxScannedPoLines = 500;

    /** Bỏ ứng viên có điểm tổng dưới ngưỡng này (0 = giữ tất cả). */
    private BigDecimal minScore = BigDecimal.ZERO;

    @PostConstruct
    public void validate() {
        if (maxPerLine <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "tmw.matching.candidate.max-per-line phải > 0");
        }
        if (maxScannedPoLines <= 0 || maxScannedPoLines > PoLineCandidateQuery.MAX_LIMIT) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "tmw.matching.candidate.max-scanned-po-lines phải trong khoảng 1.." + PoLineCandidateQuery.MAX_LIMIT);
        }
        if (minScore == null || minScore.signum() < 0 || minScore.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "tmw.matching.candidate.min-score phải trong khoảng 0..1");
        }
    }
}
