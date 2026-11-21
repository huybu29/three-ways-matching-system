package com.tmw.backend.matching.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tmw.backend.matching.api.AllocationState;
import com.tmw.backend.matching.api.MatchStatus;
import com.tmw.backend.matching.entity.MatchLine;

public interface MatchLineRepository extends JpaRepository<MatchLine, UUID> {

    List<MatchLine> findByPoLineIdAndMatch_StatusIn(UUID poLineId, Collection<MatchStatus> statuses);

    boolean existsByInvoiceLineIdAndMatch_StatusNotIn(UUID invoiceLineId, Collection<MatchStatus> statuses);

    /**
     * Tổng số lượng đang ở trạng thái phân bổ cho trước trên 1 dòng PO.
     * Dùng để đối soát chéo cột reserved/allocated của Capacity.
     */
    @Query("""
           select coalesce(sum(l.matchedQty), 0)
           from MatchLine l
           where l.poLineId = :poLineId
             and l.allocationState = :state
           """)
    BigDecimal sumMatchedQty(@Param("poLineId") UUID poLineId, @Param("state") AllocationState state);

    /**
     * Tổng số lượng của 1 dòng hóa đơn đã được ghép (chưa bị release).
     */
    @Query("""
           select coalesce(sum(l.matchedQty), 0)
           from MatchLine l
           where l.invoiceLineId = :invoiceLineId
             and l.allocationState <> com.tmw.backend.matching.api.AllocationState.RELEASED
           """)
    BigDecimal sumActiveQtyByInvoiceLine(@Param("invoiceLineId") UUID invoiceLineId);
}