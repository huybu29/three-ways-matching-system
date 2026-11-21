package com.tmw.backend.purchaseorder.service;

import com.tmw.backend.purchaseorder.api.PurchaseOrderLineCreated;
import com.tmw.backend.purchaseorder.entity.PurchaseOrder;
import com.tmw.backend.purchaseorder.entity.PurchaseOrderLine;
import com.tmw.backend.purchaseorder.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PurchaseOrder createOrder(PurchaseOrder po) {
        // 1. Lưu và flush để có po_line.id trong DB trước khi bắn event
        PurchaseOrder savedPo = purchaseOrderRepository.saveAndFlush(po);

        // 2. Phát sự kiện đồng bộ cho từng dòng PO để module allocation tạo PoLineCapacity
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
}