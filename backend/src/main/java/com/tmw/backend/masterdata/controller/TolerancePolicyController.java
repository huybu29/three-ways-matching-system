package com.tmw.backend.masterdata.controller;

import java.util.UUID;
import com.tmw.backend.masterdata.dto.TolerancePolicyRequest;
import com.tmw.backend.masterdata.dto.TolerancePolicyResponse;
import com.tmw.backend.masterdata.entity.TolerancePolicy;
import com.tmw.backend.masterdata.service.TolerancePolicyService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import com.tmw.backend.common.pagination.CursorResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tolerance-policies")
@RequiredArgsConstructor
public class TolerancePolicyController {

    private final TolerancePolicyService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MATCHER', 'APPROVER', 'POSTER', 'AUDITOR')")
    public CursorResponse<TolerancePolicyResponse> list(
            @RequestParam(name = "supplier_id", required = false) UUID supplierId,
            @RequestParam(name = "is_active", required = false) Boolean active,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        return service.list(supplierId, active, limit, cursor);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TolerancePolicyResponse> create(@Valid @RequestBody TolerancePolicyRequest request) {
        TolerancePolicy created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag("\"" + created.getVersion() + "\"")
                .body(TolerancePolicyResponse.from(created));
    }

    /** If-Match thiếu -> 428 PRECONDITION_REQUIRED; không khớp version -> 412 VERSION_CONFLICT. */
    @PutMapping("/{policyId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TolerancePolicyResponse> update(
            @PathVariable UUID policyId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody TolerancePolicyRequest request
    ) {
        TolerancePolicy updated = service.update(policyId, ifMatch, request);
        return ResponseEntity.ok()
                .eTag("\"" + updated.getVersion() + "\"")
                .body(TolerancePolicyResponse.from(updated));
    }
}
