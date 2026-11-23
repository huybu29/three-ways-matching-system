package com.tmw.backend.allocation.service;

import com.tmw.backend.allocation.api.AllocationCapacityExceededException;
import com.tmw.backend.allocation.api.AllocationLineRequest;
import com.tmw.backend.allocation.api.CapacityAllocationService;
import com.tmw.backend.allocation.api.CapacitySnapshot;
import com.tmw.backend.allocation.repository.PoLineCapacityRepository;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.purchaseorder.entity.PoLineCapacity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CapacityAllocationServiceImpl implements CapacityAllocationService {

    private final PoLineCapacityRepository capacityRepository;

    @Override
    @Transactional
    public CapacitySnapshot reserve(UUID poLineId, BigDecimal qty, BigDecimal amount) {
        validatePoLineId(poLineId);
        BigDecimal normQty = normalizePositive(qty, "qty");
        BigDecimal normAmount = normalizeNonNegative(amount, "amount");

        int updatedRows = capacityRepository.conditionalReserve(poLineId, normQty, normAmount);
        PoLineCapacity current = getCapacityOrThrow(poLineId);

        if (updatedRows == 0) {
            throw new AllocationCapacityExceededException(current.toSnapshot(), normQty, normAmount);
        }

        return current.toSnapshot();
    }

    @Override
    @Transactional
    public CapacitySnapshot confirm(UUID poLineId, BigDecimal qty, BigDecimal amount) {
        validatePoLineId(poLineId);
        BigDecimal normQty = normalizePositive(qty, "qty");
        BigDecimal normAmount = normalizeNonNegative(amount, "amount");

        int updatedRows = capacityRepository.conditionalConfirm(poLineId, normQty, normAmount);
        PoLineCapacity current = getCapacityOrThrow(poLineId);

        if (updatedRows == 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Số lượng hoặc giá trị xác nhận vượt quá phần đang giữ chỗ (reserved) của PO line: " + poLineId
            );
        }

        return current.toSnapshot();
    }

    @Override
    @Transactional
    public CapacitySnapshot release(UUID poLineId, BigDecimal qty, BigDecimal amount) {
        validatePoLineId(poLineId);
        BigDecimal normQty = normalizePositive(qty, "qty");
        BigDecimal normAmount = normalizeNonNegative(amount, "amount");

        int updatedRows = capacityRepository.conditionalRelease(poLineId, normQty, normAmount);
        PoLineCapacity current = getCapacityOrThrow(poLineId);

        if (updatedRows == 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Số lượng hoặc giá trị giải phóng vượt quá phần đang giữ chỗ (reserved) của PO line: " + poLineId
            );
        }

        return current.toSnapshot();
    }

    @Override
    @Transactional
    public CapacitySnapshot addReceived(UUID poLineId, BigDecimal qty, BigDecimal amount) {
        validatePoLineId(poLineId);
        BigDecimal normQty = normalizePositive(qty, "received_qty");
        BigDecimal normAmount = normalizeNonNegative(amount, "received_amount");

        int updatedRows = capacityRepository.conditionalAddReceived(poLineId, normQty, normAmount);
        PoLineCapacity current = getCapacityOrThrow(poLineId);

        if (updatedRows == 0) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Không tìm thấy dữ liệu hạn mức cho PO line: " + poLineId
            );
        }

        return current.toSnapshot();
    }

    @Override
    @Transactional(readOnly = true)
    public CapacitySnapshot getSnapshot(UUID poLineId) {
        validatePoLineId(poLineId);
        return getCapacityOrThrow(poLineId).toSnapshot();
    }

    @Override
    @Transactional
    public List<CapacitySnapshot> reserveBatch(List<AllocationLineRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Danh sách dòng giữ chỗ không được để trống");
        }
        for (AllocationLineRequest item : items) {
            if (item == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phần tử dòng giữ chỗ không được null");
            }
            validatePoLineId(item.poLineId());
        }

        // Sắp xếp theo poLineId tăng dần trước khi khóa/cập nhật DB để chống Deadlock
        List<AllocationLineRequest> sortedItems = items.stream()
                .sorted(Comparator.comparing(AllocationLineRequest::poLineId))
                .toList();

        Map<UUID, CapacitySnapshot> snapshotByLineId = new HashMap<>();
        for (AllocationLineRequest item : sortedItems) {
            CapacitySnapshot updated = reserve(item.poLineId(), item.qty(), item.amount());
            snapshotByLineId.put(item.poLineId(), updated);
        }

        List<CapacitySnapshot> orderedResults = new ArrayList<>(items.size());
        for (AllocationLineRequest original : items) {
            orderedResults.add(snapshotByLineId.get(original.poLineId()));
        }
        return orderedResults;
    }

    private PoLineCapacity getCapacityOrThrow(UUID poLineId) {
        return capacityRepository.findByPoLineId(poLineId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy dữ liệu hạn mức cho PO line: " + poLineId
                ));
    }

    private static void validatePoLineId(UUID poLineId) {
        if (poLineId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "poLineId không được để trống");
        }
    }

    private static BigDecimal normalizePositive(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trường " + fieldName + " phải lớn hơn 0");
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizeNonNegative(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trường " + fieldName + " không được âm");
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}