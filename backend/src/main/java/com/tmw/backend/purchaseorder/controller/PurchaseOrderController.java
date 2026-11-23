package com.tmw.backend.purchaseorder.controller;

import com.tmw.backend.common.pagination.CursorResponse;
import com.tmw.backend.purchaseorder.PoStatus;
import com.tmw.backend.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.tmw.backend.purchaseorder.dto.PurchaseOrderResponse;
import com.tmw.backend.purchaseorder.service.PurchaseOrderService;
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
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MATCHER', 'APPROVER', 'POSTER', 'AUDITOR')")
    public CursorResponse<PurchaseOrderResponse> list(
            @RequestParam(name = "supplier_id", required = false) UUID supplierId,
            @RequestParam(required = false) PoStatus status,
            @RequestParam(name = "po_number", required = false) String poNumber,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        return purchaseOrderService.list(supplierId, status, poNumber, limit, cursor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MATCHER')")
    public PurchaseOrderResponse create(@Valid @RequestBody CreatePurchaseOrderRequest request) {
        return purchaseOrderService.create(request);
    }

    @GetMapping("/{poId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MATCHER', 'APPROVER', 'POSTER', 'AUDITOR')")
    public PurchaseOrderResponse get(@PathVariable UUID poId) {
        return purchaseOrderService.getById(poId);
    }

    @PostMapping("/{poId}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER')")
    public PurchaseOrderResponse close(@PathVariable UUID poId) {
        return purchaseOrderService.close(poId);
    }
}