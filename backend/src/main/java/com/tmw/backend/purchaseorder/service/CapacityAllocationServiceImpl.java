package com.tmw.backend.purchaseorder.service;

import com.tmw.backend.purchaseorder.entity.PoLineCapacity;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class CapacityAllocationServiceImpl implements CapacityAllocationService {

    @Override
    public void reserveCapacity(PoLineCapacity capacity, BigDecimal qty, BigDecimal amount) {
        capacity.reserve(qty, amount);
    }

    @Override
    public void confirmAllocation(PoLineCapacity capacity, BigDecimal qty, BigDecimal amount) {
        capacity.confirm(qty, amount);
    }

    @Override
    public void releaseReservation(PoLineCapacity capacity, BigDecimal qty, BigDecimal amount) {
        capacity.release(qty, amount);
    }
}