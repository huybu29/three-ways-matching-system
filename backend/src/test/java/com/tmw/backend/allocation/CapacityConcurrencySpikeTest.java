package com.tmw.backend.allocation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import com.tmw.backend.allocation.api.AllocationCapacityExceededException;
import com.tmw.backend.allocation.api.CapacityAllocationService;
import com.tmw.backend.allocation.api.CapacitySnapshot;
import com.tmw.backend.allocation.repository.PoLineCapacityRepository;
import com.tmw.backend.masterdata.entity.Supplier;
import com.tmw.backend.masterdata.repository.SupplierRepository;
import com.tmw.backend.purchaseorder.CurrencyCode;
import com.tmw.backend.purchaseorder.entity.PoLineCapacity;
import com.tmw.backend.purchaseorder.entity.PurchaseOrder;
import com.tmw.backend.purchaseorder.entity.PurchaseOrderLine;
import com.tmw.backend.purchaseorder.service.PurchaseOrderService;
import com.tmw.backend.testinfra.ConcurrentTestRunner;
import com.tmw.backend.testinfra.PostgresContainerBase;
import com.tmw.backend.testinfra.TestFixtures;

@SpringBootTest
class CapacityConcurrencySpikeTest extends PostgresContainerBase {

    private static final Logger log = LoggerFactory.getLogger(CapacityConcurrencySpikeTest.class);

    private static final int CONCURRENT_THREADS = 50;
    private static final int EXPECTED_SUCCESS_THREADS = 10;
    private static final int EXPECTED_REJECTED_THREADS = 40;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private CapacityAllocationService allocationService;

    @Autowired
    private PoLineCapacityRepository capacityRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void spikeCompareConditionalUpdateVsPessimisticForUpdateUnder50Threads() throws InterruptedException {
        UUID lineIdForConditional = createSingleLinePo(new BigDecimal("100.0000"), new BigDecimal("10000.0000"));
        UUID lineIdForPessimistic = createSingleLinePo(new BigDecimal("100.0000"), new BigDecimal("10000.0000"));

        BigDecimal requestQty = new BigDecimal("10.0000");
        BigDecimal requestAmount = new BigDecimal("100000.0000");

        // 1. Chạy 50 luồng với UPDATE có điều kiện (Conditional UPDATE)
        long startConditional = System.nanoTime();
        var conditionalResult = ConcurrentTestRunner.runSameTask(
                CONCURRENT_THREADS,
                () -> allocationService.reserve(lineIdForConditional, requestQty, requestAmount)
        );
        long conditionalElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startConditional);

        verify50ThreadOutcome(lineIdForConditional, conditionalResult);

        // 2. Chạy 50 luồng với khóa bi quan (SELECT ... FOR UPDATE)
        long startForUpdate = System.nanoTime();
        var forUpdateResult = ConcurrentTestRunner.runSameTask(
                CONCURRENT_THREADS,
                () -> transactionTemplate.execute(status -> {
                    PoLineCapacity locked = capacityRepository.findByPoLineIdForUpdate(lineIdForPessimistic)
                            .orElseThrow();
                    locked.reserve(requestQty, requestAmount);
                    return locked.toSnapshot();
                })
        );
        long forUpdateElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startForUpdate);

        verify50ThreadOutcome(lineIdForPessimistic, forUpdateResult);

        // In bảng số liệu thực đo để ghi vào tài liệu quyết định khóa tuần 2
        log.info("""
                
                ==================== SPIKE 50 LUỒNG GIỮ CHỖ HẠN MỨC ====================
                | Cơ chế khóa           | Tổng luồng | Thành công | Từ chối (409) | Thời gian (ms) | Vi phạm bất biến |
                |-----------------------|------------|------------|---------------|----------------|------------------|
                | Conditional UPDATE    | {}         | {}         | {}            | {} ms          | 0                |
                | SELECT ... FOR UPDATE | {}         | {}         | {}            | {} ms          | 0                |
                ========================================================================
                """,
                CONCURRENT_THREADS, conditionalResult.successCount(), conditionalResult.failureCount(), conditionalElapsedMs,
                CONCURRENT_THREADS, forUpdateResult.successCount(), forUpdateResult.failureCount(), forUpdateElapsedMs
        );
    }

    @Test
    void shouldPreventDeadlockWhenLockingMultipleLinesInAscendingPoLineIdOrder() throws InterruptedException {
        Supplier supplier = supplierRepository.saveAndFlush(TestFixtures.supplier().build());
        PurchaseOrder po = new PurchaseOrder(
                "PO-DEADLOCK-" + TestFixtures.nextSeq(),
                supplier.getId(),
                LocalDate.now(),
                CurrencyCode.VND
        );
        po.addLine(new PurchaseOrderLine(1, "ITEM-A", "Mặt hàng A", new BigDecimal("500"), new BigDecimal("1000"), BigDecimal.ZERO));
        po.addLine(new PurchaseOrderLine(2, "ITEM-B", "Mặt hàng B", new BigDecimal("500"), new BigDecimal("1000"), BigDecimal.ZERO));
        PurchaseOrder savedPo = purchaseOrderService.createOrder(po);

        UUID lineA = savedPo.getLines().get(0).getId();
        UUID lineB = savedPo.getLines().get(1).getId();

        List<Callable<Boolean>> mixedOrderTasks = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            // 20 luồng truyền [lineA, lineB], 20 luồng truyền ngược [lineB, lineA]
            List<UUID> requestedOrder = (i % 2 == 0) ? List.of(lineA, lineB) : List.of(lineB, lineA);

            mixedOrderTasks.add(() -> transactionTemplate.execute(status -> {
                // Luôn sắp xếp theo po_line_id tăng dần trước khi khóa FOR UPDATE
                List<UUID> sortedIds = requestedOrder.stream()
                        .sorted(Comparator.naturalOrder())
                        .toList();

                for (UUID id : sortedIds) {
                    PoLineCapacity locked = capacityRepository.findByPoLineIdForUpdate(id).orElseThrow();
                    locked.reserve(new BigDecimal("10.0000"), new BigDecimal("10000.0000"));
                }
                return true;
            }));
        }

        var result = ConcurrentTestRunner.runTasks(mixedOrderTasks);

        assertThat(result.failureCount()).isZero();
        assertThat(result.successCount()).isEqualTo(40);

        PoLineCapacity capA = capacityRepository.findByPoLineId(lineA).orElseThrow();
        PoLineCapacity capB = capacityRepository.findByPoLineId(lineB).orElseThrow();
        assertThat(capA.getReservedQty()).isEqualByComparingTo("400.0000");
        assertThat(capB.getReservedQty()).isEqualByComparingTo("400.0000");
    }

    private UUID createSingleLinePo(BigDecimal orderedQty, BigDecimal unitPrice) {
        Supplier supplier = supplierRepository.saveAndFlush(TestFixtures.supplier().build());
        PurchaseOrder po = new PurchaseOrder(
                "PO-SPIKE-" + TestFixtures.nextSeq(),
                supplier.getId(),
                LocalDate.now(),
                CurrencyCode.VND
        );
        po.addLine(new PurchaseOrderLine(
                1,
                "ITEM-SPIKE",
                "Vật tư kiểm thử đồng thời",
                orderedQty,
                unitPrice,
                new BigDecimal("0.1000")
        ));

        PurchaseOrder savedPo = purchaseOrderService.createOrder(po);
        UUID poLineId = savedPo.getLines().get(0).getId();

        // Xác nhận listener sự kiện PurchaseOrderLineCreated đã tự động tạo PoLineCapacity
        assertThat(capacityRepository.existsByPoLineId(poLineId)).isTrue();
        return poLineId;
    }

    private void verify50ThreadOutcome(
            UUID poLineId,
            ConcurrentTestRunner.ConcurrentResult<CapacitySnapshot> result
    ) {
        assertThat(result.totalThreads()).isEqualTo(CONCURRENT_THREADS);
        assertThat(result.successCount()).isEqualTo(EXPECTED_SUCCESS_THREADS);
        assertThat(result.failureCount()).isEqualTo(EXPECTED_REJECTED_THREADS);

        // Toàn bộ 40 luồng thất bại phải là AllocationCapacityExceededException (không lọt lỗi khóa DB hay 500)
        List<AllocationCapacityExceededException> exceededErrors =
                result.errorsOfType(AllocationCapacityExceededException.class);
        assertThat(exceededErrors).hasSize(EXPECTED_REJECTED_THREADS);

        // Kiểm tra bất biến cuối cùng trong PostgreSQL
        PoLineCapacity finalCapacity = capacityRepository.findByPoLineId(poLineId).orElseThrow();
        BigDecimal totalUsedQty = finalCapacity.getAllocatedQty().add(finalCapacity.getReservedQty());
        BigDecimal totalUsedAmount = finalCapacity.getAllocatedAmount().add(finalCapacity.getReservedAmount());

        assertThat(finalCapacity.getReservedQty()).isEqualByComparingTo("100.0000");
        assertThat(finalCapacity.getReservedAmount()).isEqualByComparingTo("1000000.0000");
        assertThat(totalUsedQty).isLessThanOrEqualTo(finalCapacity.getOrderedQty());
        assertThat(totalUsedAmount).isLessThanOrEqualTo(finalCapacity.getOrderedAmount());
    }
}