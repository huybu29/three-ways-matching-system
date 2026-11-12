package com.tmw.backend.matching.repository;

import com.tmw.backend.matching.entity.MatchLine;
import com.tmw.backend.matching.entity.MatchLineStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface MatchLineRepository extends JpaRepository<MatchLine, UUID> {

    List<MatchLine> findByPoLineIdAndStatus(UUID poLineId, MatchLineStatus status);

    /**
     * Tổng số lượng đang ở trạng thái cho trước trên 1 dòng PO.
     * Dùng để BE-3 đối soát chéo cột reserved/allocated của Capacity.
     */
    @Query("""
           select coalesce(sum(l.matchedQty), 0)
           from MatchLine l
           where l.poLineId = :poLineId and l.status = :status
           """)
    BigDecimal sumMatchedQty(@Param("poLineId") UUID poLineId, @Param("status") MatchLineStatus status);

    /** Tổng số lượng của 1 dòng hóa đơn đã được ghép (chưa bị release). */
    @Query("""
           select coalesce(sum(l.matchedQty), 0)
           from MatchLine l
           where l.invoiceLineId = :invoiceLineId
             and l.status <> com.tmw.backend.matching.entity.MatchLineStatus.RELEASED
           """)
    BigDecimal sumActiveQtyByInvoiceLine(@Param("invoiceLineId") UUID invoiceLineId);
}
