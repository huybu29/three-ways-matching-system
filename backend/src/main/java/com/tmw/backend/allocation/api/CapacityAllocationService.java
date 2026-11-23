package com.tmw.backend.allocation.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CapacityAllocationService {

    CapacitySnapshot reserve(UUID poLineId, BigDecimal qty, BigDecimal amount);

    CapacitySnapshot confirm(UUID poLineId, BigDecimal qty, BigDecimal amount);

    CapacitySnapshot release(UUID poLineId, BigDecimal qty, BigDecimal amount);

    CapacitySnapshot addReceived(UUID poLineId, BigDecimal qty, BigDecimal amount);

    CapacitySnapshot getSnapshot(UUID poLineId);

    List<CapacitySnapshot> reserveBatch(List<AllocationLineRequest> items);
}