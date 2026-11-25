package com.tmw.backend.masterdata.service;

import com.tmw.backend.audit.api.Audited;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.common.pagination.CursorCodec;
import com.tmw.backend.common.pagination.CursorResponse;
import com.tmw.backend.masterdata.dto.TolerancePolicyRequest;
import com.tmw.backend.masterdata.dto.TolerancePolicyResponse;
import com.tmw.backend.masterdata.entity.Supplier;
import com.tmw.backend.masterdata.entity.TolerancePolicy;
import com.tmw.backend.masterdata.repository.SupplierRepository;
import com.tmw.backend.masterdata.repository.TolerancePolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
    private static final BigDecimal MAX_PERCENTAGE = new BigDecimal("100");

    private final TolerancePolicyRepository policyRepository;
    private final SupplierRepository supplierRepository;

    @Transactional
    @Audited(action = "CREATE", entityType = "TOLERANCE_POLICY")
    public TolerancePolicy create(TolerancePolicyRequest request) {
        Supplier supplier = null;
        if (request.supplierId() != null) {
            supplier = supplierRepository.findById(request.supplierId())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            "Không tìm thấy nhà cung cấp: " + request.supplierId()
                    ));
        }

        BigDecimal pricePct = parseDecimal(request.priceTolerancePct());
        BigDecimal priceAmt = parseDecimal(request.amountToleranceAbs());
        BigDecimal qtyPct = parseDecimal(request.qtyTolerancePct());
        BigDecimal qtyAmt = null;

        validatePercentage(pricePct, "price_tolerance_pct");
        validatePercentage(qtyPct, "qty_tolerance_pct");

        boolean isActive = request.active() == null || request.active();
        if (isActive) {
            if (supplier != null) {
                policyRepository.deactivateActiveBySupplierId(supplier.getId());
            } else {
                policyRepository.deactivateActiveGlobal();
            }
        }

        TolerancePolicy policy = new TolerancePolicy(
                supplier,
                request.name().trim(),
                pricePct,
                priceAmt,
                qtyPct,
                qtyAmt,
                isActive
        );

        return policyRepository.saveAndFlush(policy);
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

    private BigDecimal parseDecimal(String raw) {
        return new BigDecimal(raw.trim()).setScale(4, RoundingMode.HALF_UP);
    }

    private void validatePercentage(BigDecimal value, String fieldName) {
        if (value.compareTo(MAX_PERCENTAGE) > 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Trường " + fieldName + " không được vượt quá 100%"
            );
        }
    }
}