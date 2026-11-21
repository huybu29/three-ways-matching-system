package com.tmw.backend.common.web;

import com.tmw.backend.common.entity.VersionedEntity;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import org.springframework.util.StringUtils;

import java.util.Arrays;

public final class ETagSupport {

    private ETagSupport() {
    }

    public static String format(Integer version) {
        return version != null ? "\"" + version + "\"" : null;
    }

    public static String from(VersionedEntity entity) {
        return entity != null ? entity.getETag() : null;
    }

    public static int parseVersion(String ifMatchHeader) {
        if (!StringUtils.hasText(ifMatchHeader)) {
            throw new BusinessException(
                    ErrorCode.PRECONDITION_REQUIRED,
                    "Thiếu header bắt buộc If-Match cho thao tác cập nhật."
            );
        }
        String normalized = stripWeakAndQuotes(ifMatchHeader);
        try {
            int version = Integer.parseInt(normalized);
            if (version < 0) {
                throw new BusinessException(
                        ErrorCode.VERSION_CONFLICT,
                        "Giá trị header If-Match không hợp lệ: " + ifMatchHeader
                );
            }
            return version;
        } catch (NumberFormatException ex) {
            throw new BusinessException(
                    ErrorCode.VERSION_CONFLICT,
                    "Giá trị header If-Match không hợp lệ: " + ifMatchHeader
            );
        }
    }

    public static void verifyMatch(String ifMatchHeader, VersionedEntity entity) {
        verifyMatch(ifMatchHeader, entity != null ? entity.getVersion() : null);
    }

    public static void verifyMatch(String ifMatchHeader, Integer currentVersion) {
        if (!StringUtils.hasText(ifMatchHeader)) {
            throw new BusinessException(
                    ErrorCode.PRECONDITION_REQUIRED,
                    "Thiếu header bắt buộc If-Match cho thao tác cập nhật."
            );
        }
        if (currentVersion == null) {
            throw new BusinessException(
                    ErrorCode.VERSION_CONFLICT,
                    "Không thể xác định phiên bản hiện tại của tài nguyên."
            );
        }

        String trimmed = ifMatchHeader.trim();
        if ("*".equals(trimmed)) {
            return;
        }

        String expectedVersion = String.valueOf(currentVersion);
        boolean matched = Arrays.stream(trimmed.split(","))
                .map(ETagSupport::stripWeakAndQuotes)
                .anyMatch(expectedVersion::equals);

        if (!matched) {
            throw new BusinessException(
                    ErrorCode.VERSION_CONFLICT,
                    "Phiên bản tài nguyên đã thay đổi (If-Match=" + trimmed
                            + ", hiện tại=" + format(currentVersion) + "). Vui lòng tải lại dữ liệu mới nhất."
            );
        }
    }

    private static String stripWeakAndQuotes(String raw) {
        String s = raw.trim();
        if (s.startsWith("W/") || s.startsWith("w/")) {
            s = s.substring(2).trim();
        }
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }
}
