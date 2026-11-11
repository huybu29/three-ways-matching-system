package com.tmw.backend.exception;

import com.tmw.backend.common.entity.BaseEntity;
import com.tmw.backend.invoice.Invoice;
import com.tmw.backend.match.Match;
import com.tmw.backend.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "match_exceptions")
@Getter @Setter
public class MatchException extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private AppUser resolvedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExceptionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExceptionStatus status = ExceptionStatus.OPEN;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> details = new HashMap<>();
}