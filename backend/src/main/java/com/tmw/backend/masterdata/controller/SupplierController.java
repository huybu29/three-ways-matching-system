package com.tmw.backend.masterdata.controller;

import com.tmw.backend.common.pagination.CursorResponse;
import com.tmw.backend.masterdata.dto.SupplierRequest;
import com.tmw.backend.masterdata.dto.SupplierResponse;
import com.tmw.backend.masterdata.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MATCHER', 'APPROVER', 'POSTER', 'AUDITOR')")
    public CursorResponse<SupplierResponse> list(
            @RequestParam(name = "tax_code", required = false) String taxCode,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        return service.list(taxCode, limit, cursor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public SupplierResponse create(@Valid @RequestBody SupplierRequest request) {
        return SupplierResponse.from(service.create(request));
    }

    @GetMapping("/{supplierId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MATCHER', 'APPROVER', 'POSTER', 'AUDITOR')")
    public SupplierResponse get(@PathVariable UUID supplierId) {
        return SupplierResponse.from(service.get(supplierId));
    }
}