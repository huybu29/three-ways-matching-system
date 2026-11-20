package com.tmw.backend.purchaseorder.repository;

import com.tmw.backend.purchaseorder.PoStatus;
import com.tmw.backend.purchaseorder.entity.PurchaseOrder;
import com.tmw.backend.purchaseorder.entity.PurchaseOrderLine;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository
        extends JpaRepository<PurchaseOrder, UUID>, JpaSpecificationExecutor<PurchaseOrder> {

    boolean existsByPoNumberIgnoreCase(String poNumber);

    Optional<PurchaseOrder> findByPoNumberIgnoreCase(String poNumber);

    @EntityGraph(attributePaths = "lines")
    Optional<PurchaseOrder> findWithLinesById(UUID id);

    @EntityGraph(attributePaths = "lines")
    Optional<PurchaseOrder> findWithLinesByPoNumberIgnoreCase(String poNumber);

    @EntityGraph(attributePaths = "lines")
    List<PurchaseOrder> findAllBySupplierIdAndStatus(UUID supplierId, PoStatus status);

    @Query("""
            SELECT l.purchaseOrder.id
            FROM PurchaseOrderLine l
            WHERE l.id = :poLineId
            """)
    Optional<UUID> findPurchaseOrderIdByLineId(@Param("poLineId") UUID poLineId);

    @Query("""
            SELECT l
            FROM PurchaseOrderLine l
            JOIN FETCH l.purchaseOrder po
            WHERE l.id IN :poLineIds
            """)
    List<PurchaseOrderLine> findLinesWithPoByIds(@Param("poLineIds") Collection<UUID> poLineIds);
}