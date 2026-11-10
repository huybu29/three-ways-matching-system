package com.tmw.backend.audit;


import backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "match_exceptions")
@Getter @Setter
public class MatchException extends BaseEntity {

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "match_id")
    private UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExceptionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExceptionStatus status = ExceptionStatus.OPEN;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> details = new HashMap<>();

    @Column(name = "suggested_action")
    private String suggestedAction;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    private Integer version;
}