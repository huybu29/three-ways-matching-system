package com.tmw.backend.masterdata;

import com.tmw.backend.exception.BusinessException;
import com.tmw.backend.exception.ErrorCode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService {

    private static final Sort ORDER = Sort.by("createdAt", "id");

    private final SupplierRepository repository;

    public SupplierService(SupplierRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Supplier create(SupplierRequest request) {
        String code = request.code().trim();
        String taxCode = request.taxCode().trim();

        if (repository.existsByTaxCode(taxCode)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Mã số thuế đã tồn tại: " + taxCode);
        }
        if (repository.existsByCode(code)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Mã nhà cung cấp đã tồn tại: " + code);
        }

        try {
            return repository.saveAndFlush(new Supplier(code, request.name().trim(), taxCode, request.address()));
        } catch (DataIntegrityViolationException e) {
            // Hai request cùng lúc vượt qua bước kiểm tra: unique constraint của DB chặn bản ghi thứ hai.
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "Mã số thuế hoặc mã nhà cung cấp đã tồn tại");
        }
    }

    @Transactional(readOnly = true)
    public Supplier get(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhà cung cấp " + id));
    }

    @Transactional(readOnly = true)
    public CursorPage<Supplier> list(String taxCode, int limit, String cursor) {
        Specification<Supplier> spec = (root, query, cb) -> cb.conjunction();
        if (taxCode != null && !taxCode.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("taxCode"), taxCode.trim()));
        }
        if (cursor != null && !cursor.isBlank()) {
            CursorPage.Cursor c = decode(cursor);
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.greaterThan(root.<OffsetDateTime>get("createdAt"), c.createdAt()),
                    cb.and(cb.equal(root.<OffsetDateTime>get("createdAt"), c.createdAt()),
                            cb.greaterThan(root.<UUID>get("id"), c.id()))));
        }

        // Lấy dư 1 bản ghi để biết còn trang sau hay không, không cần câu count.
        List<Supplier> rows = repository.findBy(spec, q -> q.sortBy(ORDER).limit(limit + 1).all());
        boolean hasMore = rows.size() > limit;
        List<Supplier> items = hasMore ? rows.subList(0, limit) : rows;
        String next = hasMore
                ? CursorPage.encode(items.get(limit - 1).getCreatedAt(), items.get(limit - 1).getId())
                : null;
        return new CursorPage<>(items, next);
    }

    private static CursorPage.Cursor decode(String cursor) {
        try {
            return CursorPage.decode(cursor);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_PARAMS, "Tham số cursor không hợp lệ");
        }
    }
}
