package com.tmw.backend.masterdata;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/suppliers")
@Validated
public class SupplierController {

    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @GetMapping
    public CursorPage<SupplierResponse> list(
            @RequestParam(name = "tax_code", required = false) String taxCode,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            @RequestParam(required = false) String cursor) {
        CursorPage<Supplier> page = service.list(taxCode, limit, cursor);
        return new CursorPage<>(page.items().stream().map(SupplierResponse::from).toList(), page.nextCursor());
    }

    /** Header Idempotency-Key do IdempotencyFilter (common) kiểm tra và xử lý, controller không cần nhận. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public SupplierResponse create(@Valid @RequestBody SupplierRequest request) {
        return SupplierResponse.from(service.create(request));
    }

    @GetMapping("/{supplierId}")
    public SupplierResponse get(@PathVariable UUID supplierId) {
        return SupplierResponse.from(service.get(supplierId));
    }
}
