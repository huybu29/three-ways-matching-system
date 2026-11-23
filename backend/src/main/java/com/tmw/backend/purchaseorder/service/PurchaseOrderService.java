package com.tmw.backend.purchaseorder.service;

import com.tmw.backend.allocation.api.CapacitySnapshot;
import com.tmw.backend.allocation.repository.PoLineCapacityRepository;
import com.tmw.backend.audit.api.Audited;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.common.pagination.CursorCodec;
import com.tmw.backend.common.pagination.CursorResponse;
import com.tmw.backend.masterdata.repository.SupplierRepository;
import com.tmw.backend.purchaseorder.PoStatus;
import com.tmw.backend.purchaseorder.api.PurchaseOrderLineCreated;
import com.tmw.backend.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.tmw.backend.purchaseorder.dto.PurchaseOrderLineRequest;
import com.tmw.backend.purchaseorder.dto.PurchaseOrderResponse;
import com.tmw.backend.purchaseorder.entity.PoLineCapacity;
import com.tmw.backend.purchaseorder.entity.PurchaseOrder;
import com.tmw.backend.purchaseorder.entity.PurchaseOrderLine;
import com.tmw.backend.purchaseorder.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private static final Sort ORDER = Sort.by(Sort.Direction.ASC, "createdAt", "id");

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final PoLineCapacityRepository capacityRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Audited(entity = "PURCHASE_ORDER", action = "CREATE")
    public PurchaseOrderResponse create(CreatePurchaseOrderRequest request) {
        String normalizedPoNumber = request.poNumber().trim().toUpperCase(Locale.ROOT);
        if (purchaseOrderRepository.existsByPoNumberIgnoreCase(normalizedPoNumber)) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Số đơn đặt hàng (po_number) đã tồn tại: " + normalizedPoNumber
            );
        }

        if (!supplierRepository.existsById(request.supplierId())) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Không tìm thấy nhà cung cấp: " + request.supplierId()
            );
        }

        PurchaseOrder po = new PurchaseOrder(
                normalizedPoNumber,
                request.supplierId(),
                request.orderDate(),
                request.currency()
        );

        for (PurchaseOrderLineRequest lineReq : request.lines()) {
            po.addLine(new PurchaseOrderLine(
                    lineReq.lineNo(),
                    lineReq.itemCode(),
                    lineReq.description(),
                    lineReq.orderedQty(),
                    lineReq.unitPrice(),
                    lineReq.vatRate()
            ));
        }

        try {
            PurchaseOrder savedPo = createOrder(po);
            return buildDetailResponse(savedPo);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Số đơn đặt hàng hoặc số thứ tự dòng bị trùng lặp"
            );
        }
    }

    @Transactional
    public PurchaseOrder createOrder(PurchaseOrder po) {
        PurchaseOrder savedPo = purchaseOrderRepository.saveAndFlush(po);

        for (PurchaseOrderLine line : savedPo.getLines()) {
            eventPublisher.publishEvent(new PurchaseOrderLineCreated(
                    savedPo.getId(),
                    line.getId(),
                    line.getOrderedQty(),
                    line.getLineAmount()
            ));
        }

        return savedPo;
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getById(UUID id) {
        PurchaseOrder po = purchaseOrderRepository.findWithLinesById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy đơn đặt hàng: " + id
                ));
        return buildDetailResponse(po);
    }

    @Transactional(readOnly = true)
    public CursorResponse<PurchaseOrderResponse> list(
            UUID supplierId,
            PoStatus status,
            String poNumber,
            Integer limit,
            String cursor
    ) {
        int normalizedLimit = CursorCodec.normalizeLimit(limit);
        Specification<PurchaseOrder> spec = (root, query, cb) -> cb.conjunction();

        if (supplierId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("supplierId"), supplierId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (poNumber != null && !poNumber.isBlank()) {
            String keyword = "%" + poNumber.trim().toUpperCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.upper(root.get("poNumber")), keyword));
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

        List<PurchaseOrder> rows = purchaseOrderRepository.findBy(
                spec,
                q -> q.sortBy(ORDER).limit(normalizedLimit + 1).all()
        );
        List<PurchaseOrderResponse> dtoRows = rows.stream()
                .map(PurchaseOrderResponse::summaryFrom)
                .toList();

        return CursorResponse.of(
                dtoRows,
                normalizedLimit,
                item -> CursorCodec.encodeCompositeCursor(item.createdAt(), item.id())
        );
    }

    @Transactional
    @Audited(entity = "PURCHASE_ORDER", action = "CLOSE")
    public PurchaseOrderResponse close(UUID id) {
        PurchaseOrder po = purchaseOrderRepository.findWithLinesById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy đơn đặt hàng: " + id
                ));

        if (po.getStatus() == PoStatus.CLOSED) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Đơn đặt hàng đã ở trạng thái CLOSED"
            );
        }

        List<UUID> lineIds = po.getLines().stream().map(PurchaseOrderLine::getId).toList();
        boolean hasActiveReservation = capacityRepository.findAllByPoLineIdIn(lineIds).stream()
                .anyMatch(cap -> cap.getReservedQty().compareTo(BigDecimal.ZERO) > 0
                        || cap.getReservedAmount().compareTo(BigDecimal.ZERO) > 0);

        if (hasActiveReservation) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Không thể đóng đơn đặt hàng khi vẫn còn dòng hàng đang giữ chỗ (reserved) chưa xử lý"
            );
        }

        po.changeStatus(PoStatus.CLOSED);
        PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(po);
        return buildDetailResponse(saved);
    }

    private PurchaseOrderResponse buildDetailResponse(PurchaseOrder po) {
        List<UUID> lineIds = po.getLines().stream().map(PurchaseOrderLine::getId).toList();
        Map<UUID, CapacitySnapshot> capacityMap = capacityRepository.findAllByPoLineIdIn(lineIds).stream()
                .collect(Collectors.toMap(PoLineCapacity::getPoLineId, PoLineCapacity::toSnapshot));
        return PurchaseOrderResponse.from(po, capacityMap);
    }
}