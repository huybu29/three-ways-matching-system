package com.tmw.backend.allocation.service;

import com.tmw.backend.allocation.api.AllocationCapacityExceededException;
import com.tmw.backend.allocation.api.CapacityAllocationService;
import com.tmw.backend.allocation.api.CapacitySnapshot;
import com.tmw.backend.purchaseorder.entity.PoLineCapacity;
import com.tmw.backend.allocation.repository.PoLineCapacityRepository;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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