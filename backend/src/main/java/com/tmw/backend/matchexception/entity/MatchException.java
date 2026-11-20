package com.tmw.backend.matchexception.entity;

import com.tmw.backend.common.entity.BaseEntity;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.matchexception.api.ExceptionStatus;
import com.tmw.backend.matchexception.api.ExceptionType;
import com.tmw.backend.matchexception.api.MatchExceptionDto;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "match_exception")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchException extends BaseEntity {

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "invoice_line_id")
    private UUID invoiceLineId;

    @Column(name = "po_line_id")
    private UUID poLineId;

    @Column(name = "match_id")
    private UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false, length = 40)
    private ExceptionType exceptionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExceptionStatus status = ExceptionStatus.OPEN;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public MatchException(
            UUID invoiceId,
            UUID invoiceLineId,
            UUID poLineId,
            UUID matchId,
            ExceptionType exceptionType,
            String message,
            Map<String, Object> details
    ) {
        if (invoiceId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invoiceId không được để trống");
        }
        if (exceptionType == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "exceptionType không được để trống");
        }
        if (message == null || message.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Nội dung mô tả ngoại lệ (message) không được để trống");
        }

        this.invoiceId = invoiceId;
        this.invoiceLineId = invoiceLineId;
        this.poLineId = poLineId;
        this.matchId = matchId;
        this.exceptionType = exceptionType;
        this.status = ExceptionStatus.OPEN;
        this.message = message.trim();
        this.details = copyNullableMap(details);
        this.resolvedBy = null;
        this.resolvedAt = null;
        this.resolutionNote = null;
    }

    public Map<String, Object> getDetails() {
        return details == null ? null : Collections.unmodifiableMap(details);
    }

    /**
     * Cập nhật thông tin lần chạy đối chiếu mới nhất nếu ngoại lệ cùng khóa tự nhiên vẫn đang OPEN.
     */
    public void refreshOpenContext(UUID latestMatchId, String latestMessage, Map<String, Object> latestDetails) {
        if (this.status != ExceptionStatus.OPEN) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ được cập nhật ngữ cảnh cho ngoại lệ đang ở trạng thái OPEN"
            );
        }
        if (latestMatchId != null) {
            this.matchId = latestMatchId;
        }
        if (latestMessage != null && !latestMessage.isBlank()) {
            this.message = latestMessage.trim();
        }
        if (latestDetails != null) {
            this.details = copyNullableMap(latestDetails);
        }
    }

    public void resolve(UUID actorId, String note) {
        closeWithStatus(ExceptionStatus.RESOLVED, actorId, note);
    }

    public void waive(UUID actorId, String note) {
        closeWithStatus(ExceptionStatus.WAIVED, actorId, note);
    }

    private void closeWithStatus(ExceptionStatus targetStatus, UUID actorId, String note) {
        if (this.status != ExceptionStatus.OPEN) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    String.format("Không thể xử lý ngoại lệ %s vì trạng thái hiện tại là %s", getId(), this.status)
            );
        }
        if (actorId == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Bắt buộc có thông tin người xử lý (resolved_by) khi đóng ngoại lệ"
            );
        }
        if (note == null || note.isBlank()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Bắt buộc nhập ghi chú xử lý (resolution_note) khi đóng ngoại lệ"
            );
        }

        this.status = targetStatus;
        this.resolvedBy = actorId;
        this.resolvedAt = Instant.now();
        this.resolutionNote = note.trim();
        assertResolutionInvariant();
    }

    @PrePersist
    @PreUpdate
    void assertResolutionInvariant() {
        if (this.status == ExceptionStatus.OPEN) {
            if (this.resolvedBy != null || this.resolvedAt != null) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Ngoại lệ ở trạng thái OPEN không được phép có resolved_by hoặc resolved_at"
                );
            }
        } else {
            if (this.resolvedBy == null || this.resolvedAt == null) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Ngoại lệ đã xử lý (" + this.status + ") bắt buộc phải có cả resolved_by và resolved_at"
                );
            }
        }
    }

    public MatchExceptionDto toDto() {
        return new MatchExceptionDto(
                getId(),
                invoiceId,
                invoiceLineId,
                poLineId,
                matchId,
                exceptionType,
                status,
                message,
                getDetails(),
                resolvedBy,
                resolvedAt,
                resolutionNote,
                version
        );
    }

    private static Map<String, Object> copyNullableMap(Map<String, Object> source) {
        return source == null ? null : new LinkedHashMap<>(source);
    }
}