package com.tmw.backend.invoice;

import com.tmw.backend.common.enums.CurrencyCode;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.invoice.api.InvoiceStatus;
import com.tmw.backend.invoice.entity.Invoice;
import com.tmw.backend.invoice.entity.InvoiceLine;
import com.tmw.backend.invoice.service.InvoiceStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceStateMachineMatrixUnitTest {

    private static final Map<InvoiceStatus, Set<InvoiceStatus>> EXPECTED_TRANSITION_MATRIX = Map.of(
            InvoiceStatus.RECEIVED, Set.of(InvoiceStatus.MATCHING, InvoiceStatus.REJECTED, InvoiceStatus.VOID),
            InvoiceStatus.MATCHING, Set.of(InvoiceStatus.MATCHED, InvoiceStatus.EXCEPTION, InvoiceStatus.RECEIVED, InvoiceStatus.REJECTED, InvoiceStatus.VOID),
            InvoiceStatus.MATCHED, Set.of(InvoiceStatus.APPROVED, InvoiceStatus.MATCHING, InvoiceStatus.RECEIVED, InvoiceStatus.REJECTED, InvoiceStatus.VOID),
            InvoiceStatus.EXCEPTION, Set.of(InvoiceStatus.APPROVED, InvoiceStatus.MATCHING, InvoiceStatus.RECEIVED, InvoiceStatus.REJECTED, InvoiceStatus.VOID),
            InvoiceStatus.APPROVED, Set.of(InvoiceStatus.POSTED, InvoiceStatus.RECEIVED, InvoiceStatus.REJECTED, InvoiceStatus.VOID),
            InvoiceStatus.POSTED, Set.of(),
            InvoiceStatus.REJECTED, Set.of(),
            InvoiceStatus.VOID, Set.of()
    );

    private InvoiceStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new InvoiceStateMachine();
    }

    static Stream<Arguments> all64StatePairs() {
        List<Arguments> pairs = new ArrayList<>();
        for (InvoiceStatus source : InvoiceStatus.values()) {
            for (InvoiceStatus target : InvoiceStatus.values()) {
                boolean expectedAllowed = EXPECTED_TRANSITION_MATRIX.get(source).contains(target);
                pairs.add(Arguments.of(source, target, expectedAllowed));
            }
        }
        return pairs.stream();
    }

    @ParameterizedTest(name = "{0} -> {1} (hợp lệ = {2})")
    @MethodSource("all64StatePairs")
    void shouldEnforceFull8x8StateTransitionMatrix(
            InvoiceStatus sourceStatus,
            InvoiceStatus targetStatus,
            boolean expectedAllowed
    ) {
        Invoice invoice = createInvoiceInStatus(sourceStatus);

        if (expectedAllowed) {
            Invoice updated = stateMachine.transition(invoice, targetStatus, "Lý do hợp lệ phục vụ kiểm thử");
            assertThat(updated.getStatus()).isEqualTo(targetStatus);
        } else {
            assertThatThrownBy(() -> stateMachine.transition(invoice, targetStatus, "Lý do hợp lệ phục vụ kiểm thử"))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
            assertThat(invoice.getStatus()).isEqualTo(sourceStatus);
        }
    }

    @Test
    void shouldRequireReasonWhenRejectingVoidingOrApprovingFromException() {
        Invoice received = createInvoiceInStatus(InvoiceStatus.RECEIVED);
        assertThatThrownBy(() -> stateMachine.markRejected(received, "   "))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThatThrownBy(() -> stateMachine.markVoid(received, null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        Invoice exceptionInvoice = createInvoiceInStatus(InvoiceStatus.EXCEPTION);
        UUID approverId = UUID.randomUUID();
        assertThatThrownBy(() -> stateMachine.markApproved(exceptionInvoice, approverId, ""))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        // Phê duyệt từ EXCEPTION có lý do giải trình -> hợp lệ
        stateMachine.markApproved(exceptionInvoice, approverId, "Chấp nhận chênh lệch tỷ giá theo biên bản");
        assertThat(exceptionInvoice.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
        assertThat(exceptionInvoice.getApprovedBy()).isEqualTo(approverId);
    }

    @Test
    void shouldAllowApprovingFromMatchedWithoutReasonAndRequireActorIds() {
        Invoice matched = createInvoiceInStatus(InvoiceStatus.MATCHED);

        assertThatThrownBy(() -> stateMachine.markApproved(matched, null, null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        UUID approverId = UUID.randomUUID();
        stateMachine.markApproved(matched, approverId, null);
        assertThat(matched.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
        assertThat(matched.getApprovedBy()).isEqualTo(approverId);

        assertThatThrownBy(() -> stateMachine.markPosted(matched, null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        UUID posterId = UUID.randomUUID();
        stateMachine.markPosted(matched, posterId);
        assertThat(matched.getStatus()).isEqualTo(InvoiceStatus.POSTED);
        assertThat(matched.getPostedBy()).isEqualTo(posterId);
    }

    @Test
    void shouldResetMatchedQtyApprovedByAndReasonWhenResetToReceived() {
        Invoice invoice = createInvoiceInStatus(InvoiceStatus.EXCEPTION);
        InvoiceLine line = invoice.getLines().get(0);
        line.addMatchedQty(new BigDecimal("10.0000"));
        stateMachine.markApproved(invoice, UUID.randomUUID(), "Duyệt ngoại lệ");

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
        assertThat(invoice.getApprovedBy()).isNotNull();
        assertThat(line.getMatchedQty()).isEqualByComparingTo("10.0000");

        stateMachine.resetToReceived(invoice);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.RECEIVED);
        assertThat(invoice.getApprovedBy()).isNull();
        assertThat(invoice.getStatusReason()).isNull();
        assertThat(line.getMatchedQty()).isEqualByComparingTo("0.0000");
    }

    @Test
    void shouldRejectNullInvoiceOrNullTargetStatus() {
        assertThatThrownBy(() -> stateMachine.transition(null, InvoiceStatus.MATCHING, null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        Invoice invoice = createInvoiceInStatus(InvoiceStatus.RECEIVED);
        assertThatThrownBy(() -> stateMachine.transition(invoice, null, null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    private Invoice createInvoiceInStatus(InvoiceStatus targetStatus) {
        Invoice invoice = new Invoice(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "1C26TAA",
                "0000123",
                LocalDate.of(2026, 11, 19),
                CurrencyCode.VND,
                UUID.randomUUID()
        );
        invoice.addLine(new InvoiceLine(
                1,
                UUID.randomUUID(),
                "ITEM-001",
                "Vật tư tiêu chuẩn",
                new BigDecimal("10.0000"),
                new BigDecimal("50000.0000"),
                new BigDecimal("0.1000")
        ));

        return switch (targetStatus) {
            case RECEIVED -> invoice;
            case MATCHING -> stateMachine.markMatching(invoice);
            case MATCHED -> {
                stateMachine.markMatching(invoice);
                yield stateMachine.markMatched(invoice);
            }
            case EXCEPTION -> {
                stateMachine.markMatching(invoice);
                yield stateMachine.markException(invoice, "Lệch số lượng vượt dung sai");
            }
            case APPROVED -> {
                stateMachine.markMatching(invoice);
                stateMachine.markMatched(invoice);
                yield stateMachine.markApproved(invoice, UUID.randomUUID(), null);
            }
            case POSTED -> {
                stateMachine.markMatching(invoice);
                stateMachine.markMatched(invoice);
                stateMachine.markApproved(invoice, UUID.randomUUID(), null);
                yield stateMachine.markPosted(invoice, UUID.randomUUID());
            }
            case REJECTED -> stateMachine.markRejected(invoice, "Từ chối hóa đơn");
            case VOID -> stateMachine.markVoid(invoice, "Hủy hóa đơn");
        };
    }
}