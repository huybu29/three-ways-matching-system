package com.tmw.backend.allocation;

import com.tmw.backend.allocation.api.AllocationCapacityExceededException;
import com.tmw.backend.allocation.api.CapacitySnapshot;
import com.tmw.backend.purchaseorder.entity.PoLineCapacity;
import com.tmw.backend.allocation.listener.PurchaseOrderLineCreatedListener;
import com.tmw.backend.allocation.repository.PoLineCapacityRepository;
import com.tmw.backend.allocation.service.CapacityAllocationServiceImpl;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.purchaseorder.api.PurchaseOrderLineCreated;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllocationUnitTest {

    @Mock
    private PoLineCapacityRepository capacityRepository;

    @InjectMocks
    private CapacityAllocationServiceImpl allocationService;

    @InjectMocks
    private PurchaseOrderLineCreatedListener lineCreatedListener;

    // ==================== 1. DOMAIN INVARIANTS (PoLineCapacity) ====================

    @Test
    void shouldMaintainInvariantAcrossFullLifecycleReserveConfirmReleaseAndAddReceived() {
        UUID poLineId = UUID.randomUUID();
        PoLineCapacity capacity = new PoLineCapacity(
                poLineId,
                new BigDecimal("100"),
                new BigDecimal("1000000")
        );

        // 1. Nhập kho (GRN) 80 cái
        capacity.addReceived(new BigDecimal("80"), new BigDecimal("800000"));
        assertThat(capacity.getReceivedQty()).isEqualByComparingTo("80.0000");
        assertThat(capacity.getReceivedAmount()).isEqualByComparingTo("800000.0000");

        // 2. Giữ chỗ lần 1: 60 cái -> remaining = 40
        capacity.reserve(new BigDecimal("60"), new BigDecimal("600000"));
        assertThat(capacity.getReservedQty()).isEqualByComparingTo("60.0000");
        assertThat(capacity.getRemainingQty()).isEqualByComparingTo("40.0000");

        // 3. Ghi sổ (confirm) 40 cái từ reserved sang allocated -> reserved = 20, allocated = 40, remaining = 40
        capacity.confirm(new BigDecimal("40"), new BigDecimal("400000"));
        assertThat(capacity.getAllocatedQty()).isEqualByComparingTo("40.0000");
        assertThat(capacity.getReservedQty()).isEqualByComparingTo("20.0000");
        assertThat(capacity.getRemainingQty()).isEqualByComparingTo("40.0000");

        // 4. Hủy giữ chỗ (release) 15 cái -> reserved = 5, remaining = 55
        capacity.release(new BigDecimal("15"), new BigDecimal("150000"));
        assertThat(capacity.getReservedQty()).isEqualByComparingTo("5.0000");
        assertThat(capacity.getRemainingQty()).isEqualByComparingTo("55.0000");

        // 5. Giữ chỗ chạm đúng biên tối đa 55 cái (allocated 40 + reserved 60 == ordered 100)
        capacity.reserve(new BigDecimal("55"), new BigDecimal("550000"));
        assertThat(capacity.getAllocatedQty().add(capacity.getReservedQty()))
                .isEqualByComparingTo(capacity.getOrderedQty());
        assertThat(capacity.getRemainingQty()).isEqualByComparingTo("0.0000");
        assertThat(capacity.getRemainingAmount()).isEqualByComparingTo("0.0000");
    }

    @Test
    void shouldThrowAllocationCapacityExceededExceptionWithCurrentSnapshotWhenQtyOrAmountExceedsLimit() {
        UUID poLineId = UUID.randomUUID();
        PoLineCapacity capacity = new PoLineCapacity(
                poLineId,
                new BigDecimal("100"),
                new BigDecimal("1000000")
        );
        capacity.reserve(new BigDecimal("70"), new BigDecimal("700000"));

        // Vượt hạn mức số lượng (còn 30 nhưng xin 31)
        assertThatThrownBy(() -> capacity.reserve(new BigDecimal("31"), new BigDecimal("200000")))
                .isInstanceOfSatisfying(AllocationCapacityExceededException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ALLOCATION_CAPACITY_EXCEEDED);
                    assertThat(ex.getCurrentCapacity().poLineId()).isEqualTo(poLineId);
                    assertThat(ex.getCurrentCapacity().remainingQty()).isEqualByComparingTo("30.0000");
                    assertThat(ex.getCurrentCapacity().remainingAmount()).isEqualByComparingTo("300000.0000");
                    assertThat(ex.getRequestedQty()).isEqualByComparingTo("31.0000");
                });

        // Vượt hạn mức số tiền (còn 300,000 nhưng xin 350,000 dù số lượng vẫn đủ)
        assertThatThrownBy(() -> capacity.reserve(new BigDecimal("10"), new BigDecimal("350000")))
                .isInstanceOfSatisfying(AllocationCapacityExceededException.class, ex -> {
                    assertThat(ex.getCurrentCapacity().remainingAmount()).isEqualByComparingTo("300000.0000");
                    assertThat(ex.getRequestedAmount()).isEqualByComparingTo("350000.0000");
                });
    }

    @Test
    void shouldRejectConfirmOrReleaseExceedingReservedBalance() {
        PoLineCapacity capacity = new PoLineCapacity(UUID.randomUUID(), new BigDecimal("100"), new BigDecimal("1000000"));
        capacity.reserve(new BigDecimal("20"), new BigDecimal("200000"));

        assertThatThrownBy(() -> capacity.confirm(new BigDecimal("21"), new BigDecimal("200000")))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));

        assertThatThrownBy(() -> capacity.release(new BigDecimal("20"), new BigDecimal("200001")))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void shouldRejectInvalidConstructorAndNegativeOrZeroArguments() {
        assertThatThrownBy(() -> new PoLineCapacity(null, BigDecimal.TEN, BigDecimal.TEN))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new PoLineCapacity(UUID.randomUUID(), BigDecimal.ZERO, BigDecimal.TEN))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new PoLineCapacity(UUID.randomUUID(), BigDecimal.TEN, new BigDecimal("-1")))
                .isInstanceOf(BusinessException.class);

        PoLineCapacity valid = new PoLineCapacity(UUID.randomUUID(), BigDecimal.TEN, BigDecimal.TEN);
        assertThatThrownBy(() -> valid.reserve(BigDecimal.ZERO, BigDecimal.ONE))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> valid.addReceived(new BigDecimal("-2"), BigDecimal.ONE))
                .isInstanceOf(BusinessException.class);
    }

    // ==================== 2. SERVICE (CONDITIONAL UPDATE BRANCHES) ====================

    @Test
    void shouldReturnUpdatedSnapshotWhenConditionalReserveSucceeds() {
        UUID poLineId = UUID.randomUUID();
        PoLineCapacity updatedEntity = new PoLineCapacity(poLineId, new BigDecimal("100"), new BigDecimal("1000000"));
        updatedEntity.reserve(new BigDecimal("25"), new BigDecimal("250000"));

        when(capacityRepository.conditionalReserve(
                poLineId,
                new BigDecimal("25.0000"),
                new BigDecimal("250000.0000")
        )).thenReturn(1);
        when(capacityRepository.findByPoLineId(poLineId)).thenReturn(Optional.of(updatedEntity));

        CapacitySnapshot snapshot = allocationService.reserve(poLineId, new BigDecimal("25"), new BigDecimal("250000"));

        assertThat(snapshot.reservedQty()).isEqualByComparingTo("25.0000");
        assertThat(snapshot.remainingQty()).isEqualByComparingTo("75.0000");
    }

    @Test
    void shouldThrowAllocationCapacityExceededWithCurrentCapacityWhenConditionalReserveReturns0() {
        UUID poLineId = UUID.randomUUID();
        PoLineCapacity currentEntity = new PoLineCapacity(poLineId, new BigDecimal("100"), new BigDecimal("1000000"));
        currentEntity.reserve(new BigDecimal("90"), new BigDecimal("900000"));

        when(capacityRepository.conditionalReserve(
                poLineId,
                new BigDecimal("15.0000"),
                new BigDecimal("150000.0000")
        )).thenReturn(0);
        when(capacityRepository.findByPoLineId(poLineId)).thenReturn(Optional.of(currentEntity));

        assertThatThrownBy(() -> allocationService.reserve(poLineId, new BigDecimal("15"), new BigDecimal("150000")))
                .isInstanceOfSatisfying(AllocationCapacityExceededException.class, ex -> {
                    assertThat(ex.getCurrentCapacity().poLineId()).isEqualTo(poLineId);
                    assertThat(ex.getCurrentCapacity().reservedQty()).isEqualByComparingTo("90.0000");
                    assertThat(ex.getCurrentCapacity().remainingQty()).isEqualByComparingTo("10.0000");
                });
    }

    @Test
    void shouldThrowResourceNotFoundWhenPoLineCapacityDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(capacityRepository.conditionalReserve(any(), any(), any())).thenReturn(0);
        when(capacityRepository.findByPoLineId(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> allocationService.reserve(missingId, BigDecimal.ONE, BigDecimal.TEN))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void shouldThrowInvalidStateTransitionWhenConditionalConfirmOrReleaseReturns0() {
        UUID poLineId = UUID.randomUUID();
        PoLineCapacity currentEntity = new PoLineCapacity(poLineId, new BigDecimal("100"), new BigDecimal("1000000"));

        when(capacityRepository.conditionalConfirm(any(), any(), any())).thenReturn(0);
        when(capacityRepository.conditionalRelease(any(), any(), any())).thenReturn(0);
        when(capacityRepository.findByPoLineId(poLineId)).thenReturn(Optional.of(currentEntity));

        assertThatThrownBy(() -> allocationService.confirm(poLineId, BigDecimal.TEN, BigDecimal.TEN))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));

        assertThatThrownBy(() -> allocationService.release(poLineId, BigDecimal.TEN, BigDecimal.TEN))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void shouldAccumulateReceivedCapacityViaService() {
        UUID poLineId = UUID.randomUUID();
        PoLineCapacity afterReceipt = new PoLineCapacity(poLineId, new BigDecimal("100"), new BigDecimal("1000000"));
        afterReceipt.addReceived(new BigDecimal("45"), new BigDecimal("450000"));

        when(capacityRepository.conditionalAddReceived(
                poLineId,
                new BigDecimal("45.0000"),
                new BigDecimal("450000.0000")
        )).thenReturn(1);
        when(capacityRepository.findByPoLineId(poLineId)).thenReturn(Optional.of(afterReceipt));

        CapacitySnapshot snapshot = allocationService.addReceived(poLineId, new BigDecimal("45"), new BigDecimal("450000"));

        assertThat(snapshot.receivedQty()).isEqualByComparingTo("45.0000");
        assertThat(snapshot.receivedAmount()).isEqualByComparingTo("450000.0000");
    }

    // ==================== 3. EVENT LISTENER (PurchaseOrderLineCreatedListener) ====================

    @Test
    void shouldCreatePoLineCapacityWhenReceivingPurchaseOrderLineCreatedEvent() {
        UUID poId = UUID.randomUUID();
        UUID poLineId = UUID.randomUUID();
        PurchaseOrderLineCreated event = new PurchaseOrderLineCreated(
                poId, poLineId, new BigDecimal("50"), new BigDecimal("2500000")
        );

        when(capacityRepository.existsByPoLineId(poLineId)).thenReturn(false);

        lineCreatedListener.onPurchaseOrderLineCreated(event);

        ArgumentCaptor<PoLineCapacity> captor = ArgumentCaptor.forClass(PoLineCapacity.class);
        verify(capacityRepository).save(captor.capture());

        PoLineCapacity saved = captor.getValue();
        assertThat(saved.getPoLineId()).isEqualTo(poLineId);
        assertThat(saved.getOrderedQty()).isEqualByComparingTo("50.0000");
        assertThat(saved.getOrderedAmount()).isEqualByComparingTo("2500000.0000");
        assertThat(saved.getAllocatedQty()).isEqualByComparingTo("0.0000");
        assertThat(saved.getReservedQty()).isEqualByComparingTo("0.0000");
    }

    @Test
    void shouldSkipDuplicateCreationIfCapacityAlreadyExists() {
        UUID poLineId = UUID.randomUUID();
        PurchaseOrderLineCreated event = new PurchaseOrderLineCreated(
                UUID.randomUUID(), poLineId, BigDecimal.TEN, BigDecimal.TEN
        );

        when(capacityRepository.existsByPoLineId(poLineId)).thenReturn(true);

        lineCreatedListener.onPurchaseOrderLineCreated(event);

        verify(capacityRepository, never()).save(any());
    }
}