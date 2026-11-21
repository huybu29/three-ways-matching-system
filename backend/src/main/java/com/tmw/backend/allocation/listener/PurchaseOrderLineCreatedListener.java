package com.tmw.backend.allocation.listener;

import com.tmw.backend.purchaseorder.entity.PoLineCapacity;
import com.tmw.backend.allocation.repository.PoLineCapacityRepository;
import com.tmw.backend.purchaseorder.api.PurchaseOrderLineCreated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseOrderLineCreatedListener {

    private final PoLineCapacityRepository capacityRepository;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onPurchaseOrderLineCreated(PurchaseOrderLineCreated event) {
        if (capacityRepository.existsByPoLineId(event.poLineId())) {
            log.warn("PoLineCapacity đã tồn tại cho po_line_id={}, bỏ qua khởi tạo lặp", event.poLineId());
            return;
        }

        PoLineCapacity capacity = new PoLineCapacity(
                event.poLineId(),
                event.orderedQty(),
                event.orderedAmount()
        );
        capacityRepository.save(capacity);

        log.debug("Đã khởi tạo PoLineCapacity cho po_line_id={}, orderedQty={}, orderedAmount={}",
                event.poLineId(), event.orderedQty(), event.orderedAmount());
    }
}