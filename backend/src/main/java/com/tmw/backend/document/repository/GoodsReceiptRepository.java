package com.tmw.backend.document.repository;

import com.tmw.backend.document.entity.GoodsReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, UUID> {
    Optional<GoodsReceipt> findByGrnNumber(String grnNumber);
}