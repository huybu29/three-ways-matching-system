package com.tmw.backend.purchaseorder.service;

import com.tmw.backend.purchaseorder.entity.PoLineCapacity;
import java.math.BigDecimal;

public interface CapacityAllocationService {
    void reserveCapacity(PoLineCapacity capacity, BigDecimal qty, BigDecimal amount);
    void confirmAllocation(PoLineCapacity capacity, BigDecimal qty, BigDecimal amount);
    void releaseReservation(PoLineCapacity capacity, BigDecimal qty, BigDecimal amount);
}