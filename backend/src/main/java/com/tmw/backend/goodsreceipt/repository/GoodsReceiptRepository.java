package com.tmw.backend.goodsreceipt.repository;

import com.tmw.backend.goodsreceipt.entity.GoodsReceipt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, UUID> {

    boolean existsByGrnNumberIgnoreCase(String grnNumber);

    @EntityGraph(attributePaths = "lines")
    Optional<GoodsReceipt> findWithLinesById(UUID id);

    @EntityGraph(attributePaths = "lines")
    List<GoodsReceipt> findAllByPurchaseOrderId(UUID purchaseOrderId);

    @Query("""
            SELECT COALESCE(SUM(l.receivedQty), 0)
            FROM GoodsReceiptLine l
            WHERE l.poLineId = :poLineId
            """)
    BigDecimal sumReceivedQtyByPoLineId(@Param("poLineId") UUID poLineId);
}