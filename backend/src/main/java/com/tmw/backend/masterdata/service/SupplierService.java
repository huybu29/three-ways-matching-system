package com.tmw.backend.masterdata.service;

import com.tmw.backend.audit.api.Audited;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.common.pagination.CursorCodec;
import com.tmw.backend.common.pagination.CursorResponse;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.masterdata.dto.SupplierRequest;
import com.tmw.backend.masterdata.dto.SupplierResponse;
import com.tmw.backend.masterdata.repository.SupplierRepository;
import com.tmw.backend.masterdata.entity.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private static final Sort ORDER = Sort.by(Sort.Direction.ASC, "createdAt", "id");

    private final SupplierRepository repository;

    @Transactional
    @Audited(entity = "SUPPLIER", action = "CREATE")
    public Supplier create(SupplierRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        String taxCode = request.taxCode().trim();
        String name = request.name().trim();
        String address = (request.address() == null || request.address().isBlank())
                ? null
                : request.address().trim();

        if (repository.existsByTaxCode(taxCode)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Mã số thuế đã tồn tại: " + taxCode);
        }
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Mã nhà cung cấp đã tồn tại: " + code);
        }

        try {
            return repository.saveAndFlush(new Supplier(code, name, taxCode, address));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Mã số thuế hoặc mã nhà cung cấp đã tồn tại"
            );
        }
    }

    @Transactional(readOnly = true)
    public Supplier get(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhà cung cấp: " + id));
    }

    @Transactional(readOnly = true)
    public CursorResponse<SupplierResponse> list(String taxCode, Integer limit, String cursor) {
        int normalizedLimit = CursorCodec.normalizeLimit(limit);
        Specification<Supplier> spec = (root, query, cb) -> cb.conjunction();

        if (taxCode != null && !taxCode.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("taxCode"), taxCode.trim()));
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

        List<Supplier> rows = repository.findBy(spec, q -> q.sortBy(ORDER).limit(normalizedLimit + 1).all());
        List<SupplierResponse> dtoRows = rows.stream().map(SupplierResponse::from).toList();

        return CursorResponse.of(
                dtoRows,
                normalizedLimit,
                item -> CursorCodec.encodeCompositeCursor(item.createdAt(), item.id())
        );
    }
}