package com.tmw.backend.allocation.repository;

import com.tmw.backend.purchaseorder.entity.PoLineCapacity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;


import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface PoLineCapacityRepository extends JpaRepository<PoLineCapacity, UUID> {

    boolean existsByPoLineId(UUID poLineId);

    Optional<PoLineCapacity> findByPoLineId(UUID poLineId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PoLineCapacity c
            SET c.reservedQty = c.reservedQty + :qty,
                c.reservedAmount = c.reservedAmount + :amount,
                c.version = c.version + 1
            WHERE c.poLineId = :poLineId
              AND (c.allocatedQty + c.reservedQty + :qty) <= c.orderedQty
              AND (c.allocatedAmount + c.reservedAmount + :amount) <= c.orderedAmount
            """)
    int conditionalReserve(
            @Param("poLineId") UUID poLineId,
            @Param("qty") BigDecimal qty,
            @Param("amount") BigDecimal amount
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PoLineCapacity c
            SET c.reservedQty = c.reservedQty - :qty,
                c.allocatedQty = c.allocatedQty + :qty,
                c.reservedAmount = c.reservedAmount - :amount,
                c.allocatedAmount = c.allocatedAmount + :amount,
                c.version = c.version + 1
            WHERE c.poLineId = :poLineId
              AND c.reservedQty >= :qty
              AND c.reservedAmount >= :amount
              AND (c.allocatedQty + :qty) <= c.orderedQty
              AND (c.allocatedAmount + :amount) <= c.orderedAmount
            """)
    int conditionalConfirm(
            @Param("poLineId") UUID poLineId,
            @Param("qty") BigDecimal qty,
            @Param("amount") BigDecimal amount
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PoLineCapacity c
            SET c.reservedQty = c.reservedQty - :qty,
                c.reservedAmount = c.reservedAmount - :amount,
                c.version = c.version + 1
            WHERE c.poLineId = :poLineId
              AND c.reservedQty >= :qty
              AND c.reservedAmount >= :amount
            """)
    int conditionalRelease(
            @Param("poLineId") UUID poLineId,
            @Param("qty") BigDecimal qty,
            @Param("amount") BigDecimal amount
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PoLineCapacity c
            SET c.receivedQty = c.receivedQty + :qty,
                c.receivedAmount = c.receivedAmount + :amount,
                c.version = c.version + 1
            WHERE c.poLineId = :poLineId
            """)
    int conditionalAddReceived(
            @Param("poLineId") UUID poLineId,
            @Param("qty") BigDecimal qty,
            @Param("amount") BigDecimal amount
    );
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM PoLineCapacity c WHERE c.poLineId = :poLineId")
    Optional<PoLineCapacity> findByPoLineIdForUpdate(@Param("poLineId") UUID poLineId);
}