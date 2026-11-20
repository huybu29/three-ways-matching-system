package com.tmw.backend.masterdata.repository;

import com.tmw.backend.masterdata.entity.TolerancePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TolerancePolicyRepository
        extends JpaRepository<TolerancePolicy, UUID>, JpaSpecificationExecutor<TolerancePolicy> {

    Optional<TolerancePolicy> findFirstByIsDefaultTrue();

    boolean existsByIsDefaultTrue();

    boolean existsByIsDefaultTrueAndIdNot(UUID id);

    Optional<TolerancePolicy> findFirstBySupplierIdAndItemCategoryIsNull(UUID supplierId);

    Optional<TolerancePolicy> findFirstBySupplierIdAndItemCategoryIgnoreCase(UUID supplierId, String itemCategory);

    Optional<TolerancePolicy> findFirstBySupplierIsNullAndItemCategoryIgnoreCase(String itemCategory);

    @Modifying
    @Query("""
            UPDATE TolerancePolicy p
            SET p.active = false
            WHERE p.supplier.id = :supplierId
              AND p.active = true
            """)
    int deactivateActiveBySupplierId(@Param("supplierId") UUID supplierId);

    @Modifying
    @Query("""
            UPDATE TolerancePolicy p
            SET p.active = false
            WHERE p.supplier IS NULL
              AND p.active = true
            """)
    int deactivateActiveGlobal();

    /**
     * Lấy danh sách chính sách ứng viên phù hợp với chứng từ (theo NCC, nhóm hàng, khoảng tiền hoặc mặc định)
     * để ToleranceEvaluationService chọn theo thứ tự ưu tiên ở Giai đoạn 2.
     */
    @Query("""
            SELECT p FROM TolerancePolicy p
            WHERE (p.supplier.id = :supplierId OR p.supplier IS NULL)
              AND (p.itemCategory IS NULL OR LOWER(p.itemCategory) = LOWER(:itemCategory))
              AND (p.minAmount IS NULL OR p.minAmount <= :amount)
              AND (p.maxAmount IS NULL OR p.maxAmount >= :amount)
            """)
    List<TolerancePolicy> findCandidatePolicies(
            @Param("supplierId") UUID supplierId,
            @Param("itemCategory") String itemCategory,
            @Param("amount") BigDecimal amount
    );
}