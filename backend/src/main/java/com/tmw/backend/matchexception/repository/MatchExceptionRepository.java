package com.tmw.backend.matchexception.repository;

import com.tmw.backend.matchexception.api.ExceptionStatus;
import com.tmw.backend.matchexception.api.ExceptionType;
import com.tmw.backend.matchexception.entity.MatchException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchExceptionRepository extends JpaRepository<MatchException, UUID> {

    @Query("""
            SELECT e FROM MatchException e
            WHERE e.invoiceId = :invoiceId
              AND e.exceptionType = :exceptionType
              AND e.status = com.tmw.backend.matchexception.api.ExceptionStatus.OPEN
              AND ((:invoiceLineId IS NULL AND e.invoiceLineId IS NULL) OR e.invoiceLineId = :invoiceLineId)
              AND ((:poLineId IS NULL AND e.poLineId IS NULL) OR e.poLineId = :poLineId)
            """)
    Optional<MatchException> findOpenByNaturalKey(
            @Param("invoiceId") UUID invoiceId,
            @Param("invoiceLineId") UUID invoiceLineId,
            @Param("poLineId") UUID poLineId,
            @Param("exceptionType") ExceptionType exceptionType
    );

    List<MatchException> findAllByInvoiceIdAndStatus(UUID invoiceId, ExceptionStatus status);
}