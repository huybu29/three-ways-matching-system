package com.tmw.backend.masterdata.service;

import com.tmw.backend.audit.api.Audited;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.common.pagination.CursorCodec;
import com.tmw.backend.common.pagination.CursorResponse;
import com.tmw.backend.masterdata.config.PolicyCacheConfig;
import com.tmw.backend.masterdata.dto.TolerancePolicyRequest;
import com.tmw.backend.masterdata.dto.TolerancePolicyResponse;
import com.tmw.backend.masterdata.entity.Supplier;
import com.tmw.backend.masterdata.entity.TolerancePolicy;
import com.tmw.backend.masterdata.repository.SupplierRepository;
import com.tmw.backend.masterdata.repository.TolerancePolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TolerancePolicyService {

    private static final Sort ORDER = Sort.by(Sort.Direction.ASC, "createdAt", "id");

    private final TolerancePolicyRepository policyRepository;
    private final SupplierRepository supplierRepository;

    @Transactional
    @CacheEvict(cacheNames = PolicyCacheConfig.TOLERANCE_POLICIES, allEntries = true)
    @Audited(action = "CREATE", entityType = "TOLERANCE_POLICY")
    public TolerancePolicy create(TolerancePolicyRequest request) {
        Supplier supplier = findSupplier(request.supplierId());
        boolean isDefault = Boolean.TRUE.equals(request.isDefault());
        if (isDefault && policyRepository.existsByIsDefaultTrue()) {
            throw defaultExists();
        }

        TolerancePolicy policy = new TolerancePolicy(
                request.name(),
                supplier,
                request.itemCategory(),
                parseDecimal(request.minAmount()),
                parseDecimal(request.maxAmount()),
                parseDecimal(request.qtyTolerancePct()),
                parseDecimal(request.priceTolerancePct()),
                orZero(parseDecimal(request.amountToleranceAbs())),
                request.requireGrn() == null || request.requireGrn(),
                isDefault
        );
        if (Boolean.FALSE.equals(request.active())) {
            policy.deactivate();
        }
        return policyRepository.saveAndFlush(policy);
    }

    /** PUT /tolerance-policies/{id}: thay thế toàn bộ, bắt buộc If-Match khớp version hiện tại. */
    @Transactional
    @CacheEvict(cacheNames = PolicyCacheConfig.TOLERANCE_POLICIES, allEntries = true)
    @Audited(action = "UPDATE", entityType = "TOLERANCE_POLICY")
    public TolerancePolicy update(UUID policyId, String ifMatch, TolerancePolicyRequest request) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new BusinessException(ErrorCode.PRECONDITION_REQUIRED, "Thiếu header If-Match");
        }
        TolerancePolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Không tìm thấy chính sách dung sai: " + policyId));

        Integer expectedVersion = parseIfMatch(ifMatch);
        if (!policy.getVersion().equals(expectedVersion)) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT,
                    "Chính sách đã được thay đổi (version hiện tại: " + policy.getVersion() + ")");
        }

        Supplier supplier = findSupplier(request.supplierId());
        boolean isDefault = Boolean.TRUE.equals(request.isDefault());
        if (isDefault && policyRepository.existsByIsDefaultTrueAndIdNot(policyId)) {
            throw defaultExists();
        }

        policy.update(
                request.name(),
                supplier,
                request.itemCategory(),
                parseDecimal(request.minAmount()),
                parseDecimal(request.maxAmount()),
                parseDecimal(request.qtyTolerancePct()),
                parseDecimal(request.priceTolerancePct()),
                orZero(parseDecimal(request.amountToleranceAbs())),
                request.requireGrn() == null || request.requireGrn(),
                isDefault
        );
        if (request.active() != null) {
            if (request.active()) {
                policy.activate();
            } else {
                policy.deactivate();
            }
        }

        try {
            return policyRepository.saveAndFlush(policy);
        } catch (ObjectOptimisticLockingFailureException e) {
            // Có người khác sửa cùng lúc (race giữa lúc kiểm tra và lúc ghi)
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "Chính sách vừa bị người khác cập nhật");
        } catch (DataIntegrityViolationException e) {
            throw defaultExists();
        }
    }

    @Transactional(readOnly = true)
    public CursorResponse<TolerancePolicyResponse> list(
            UUID supplierId,
            Boolean active,
            Integer limit,
            String cursor
    ) {
        int normalizedLimit = CursorCodec.normalizeLimit(limit);
        Specification<TolerancePolicy> spec = (root, query, cb) -> cb.conjunction();

        if (supplierId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("supplier").get("id"), supplierId));
        }

        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }

        if (cursor != null && !cursor.isBlank()) {
            CursorCodec.CompositeCursor c = CursorCodec.decodeCompositeCursor(cursor);
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.greaterThan(root.get("createdAt"), c.timestamp()),
                    cb.and(
                            cb.equal(root.get("createdAt"), c.timestamp()),
                            cb.greaterThan(root.get("id"), c.id())
                    )
            ));
        }

        List<TolerancePolicy> rows = policyRepository.findBy(
                spec,
                q -> q.sortBy(ORDER).limit(normalizedLimit + 1).all()
        );

        List<TolerancePolicyResponse> dtoRows = rows.stream()
                .map(TolerancePolicyResponse::from)
                .toList();

        return CursorResponse.of(
                dtoRows,
                normalizedLimit,
                item -> CursorCodec.encodeCompositeCursor(item.createdAt(), item.id())
        );
    }

    private Supplier findSupplier(UUID supplierId) {
        if (supplierId == null) {
            return null;
        }
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy nhà cung cấp: " + supplierId));
    }

    /** If-Match có dạng "3" (có thể kèm W/ và dấu nháy kép). */
    private static Integer parseIfMatch(String header) {
        String v = header.trim();
        if (v.startsWith("W/")) {
            v = v.substring(2);
        }
        v = v.replace("\"", "").trim();
        try {
            return Integer.valueOf(v);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "If-Match không hợp lệ: " + header);
        }
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return new BigDecimal(raw.trim()).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP) : v;
    }

    private static BusinessException defaultExists() {
        return new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                "Toàn hệ thống chỉ được có tối đa một chính sách mặc định");
    }
}
