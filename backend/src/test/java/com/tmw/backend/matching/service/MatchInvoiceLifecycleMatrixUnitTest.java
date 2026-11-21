package com.tmw.backend.matching.service;

import com.tmw.backend.common.enums.CurrencyCode;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.invoice.api.InvoiceLifecycleApi;
import com.tmw.backend.invoice.api.InvoiceStatus;
import com.tmw.backend.invoice.entity.Invoice;
import com.tmw.backend.invoice.entity.InvoiceLine;
import com.tmw.backend.invoice.repository.InvoiceRepository;
import com.tmw.backend.invoice.service.InvoiceLifecycleServiceImpl;
import com.tmw.backend.invoice.service.InvoiceStateMachine;
import com.tmw.backend.matching.api.MatchProposal;
import com.tmw.backend.matching.api.MatchSource;
import com.tmw.backend.matching.api.MatchStatus;
import com.tmw.backend.matching.entity.Match;
import com.tmw.backend.matching.repository.MatchProposalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Full synchronized state transition matrix tests between Match (BE-6) and Invoice (BE-4).
 * Covers TWM-40: Match PROPOSED -> Invoice MATCHED, APPROVED -> APPROVED, POSTED -> POSTED,
 * REJECTED -> REJECTED, CANCELLED -> RECEIVED, and INVALID_STATE_TRANSITION on all invalid pairs.
 */
@ExtendWith(MockitoExtension.class)
class MatchInvoiceLifecycleMatrixUnitTest {

    enum LifecycleAction {
        PROPOSE,
        APPROVE,
        POST,
        REJECT,
        CANCEL
    }

    @Mock
    private MatchProposalRepository matchProposalRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    private MatchStateMachine matchStateMachine;
    private InvoiceStateMachine invoiceStateMachine;
    private MatchLifecycleServiceImpl matchLifecycleService;

    @BeforeEach
    void setUp() {
        matchStateMachine = new MatchStateMachine();
        invoiceStateMachine = new InvoiceStateMachine();
        InvoiceLifecycleApi invoiceLifecycleApi = new InvoiceLifecycleServiceImpl(invoiceRepository, invoiceStateMachine);
        matchLifecycleService = new MatchLifecycleServiceImpl(
                matchProposalRepository,
                matchStateMachine,
                invoiceLifecycleApi
        );
    }

    static Stream<Arguments> fullMatchInvoiceTransitionMatrix() {
        List<Arguments> cases = new ArrayList<>();
        for (MatchStatus matchStatus : MatchStatus.values()) {
            for (InvoiceStatus invoiceStatus : InvoiceStatus.values()) {
                for (LifecycleAction action : LifecycleAction.values()) {
                    boolean matchValid = isMatchTransitionValid(matchStatus, action);
                    InvoiceStatus expectedInvoiceTarget = targetInvoiceStatus(action);
                    boolean invoiceValid = invoiceStatus.canTransitionTo(expectedInvoiceTarget);
                    boolean expectedSuccess = matchValid && invoiceValid;
                    cases.add(Arguments.of(matchStatus, invoiceStatus, action, expectedSuccess));
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "[{2}] Match={0} + Invoice={1} => valid={3}")
    @MethodSource("fullMatchInvoiceTransitionMatrix")
    void shouldEnforceFullSynchronizedMatrixBetweenMatchAndInvoice(
            MatchStatus initialMatchStatus,
            InvoiceStatus initialInvoiceStatus,
            LifecycleAction action,
            boolean expectedSuccess
    ) {
        UUID matchId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        Match match = createMatchInStatus(matchId, invoiceId, initialMatchStatus);
        Invoice invoice = createInvoiceInStatus(invoiceId, initialInvoiceStatus);

        lenient().when(matchProposalRepository.findWithLinesById(matchId)).thenReturn(Optional.of(match));
        lenient().when(matchProposalRepository.saveAndFlush(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        lenient().when(invoiceRepository.findWithLinesById(invoiceId)).thenReturn(Optional.of(invoice));
        lenient().when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        if (expectedSuccess) {
            MatchProposal updated = executeAction(action, matchId, actorId);
            assertThat(updated.status()).isEqualTo(targetMatchStatus(action));
            assertThat(invoice.getStatus()).isEqualTo(targetInvoiceStatus(action));
        } else {
            assertThatThrownBy(() -> executeAction(action, matchId, actorId))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }
    }

    @Test
    void shouldExecuteFullHappyPathAndCancelResetFlow() {
        UUID matchId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        UUID posterId = UUID.randomUUID();

        Match match = createMatchInStatus(matchId, invoiceId, MatchStatus.PROPOSED);
        Invoice invoice = createInvoiceInStatus(invoiceId, InvoiceStatus.MATCHING);

        when(matchProposalRepository.findWithLinesById(matchId)).thenReturn(Optional.of(match));
        when(matchProposalRepository.saveAndFlush(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. PROPOSED -> Invoice MATCHED
        MatchProposal proposed = matchLifecycleService.markProposed(matchId);
        assertThat(proposed.status()).isEqualTo(MatchStatus.PROPOSED);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.MATCHED);

        // 2. APPROVED -> Invoice APPROVED
        MatchProposal approved = matchLifecycleService.markApproved(matchId, approverId, "Khớp chứng từ");
        assertThat(approved.status()).isEqualTo(MatchStatus.APPROVED);
        assertThat(approved.approvedBy()).isEqualTo(approverId);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
        assertThat(invoice.getApprovedBy()).isEqualTo(approverId);

        // 3. POSTED -> Invoice POSTED
        MatchProposal posted = matchLifecycleService.markPosted(matchId, posterId);
        assertThat(posted.status()).isEqualTo(MatchStatus.POSTED);
        assertThat(posted.postedBy()).isEqualTo(posterId);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.POSTED);
        assertThat(invoice.getPostedBy()).isEqualTo(posterId);
    }

    @Test
    void shouldResetInvoiceToReceivedAndClearMatchedQtyWhenMatchCancelled() {
        UUID matchId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        Match match = createMatchInStatus(matchId, invoiceId, MatchStatus.PROPOSED);
        Invoice invoice = createInvoiceInStatus(invoiceId, InvoiceStatus.MATCHED);
        invoice.getLines().get(0).addMatchedQty(new BigDecimal("5.0000"));

        when(matchProposalRepository.findWithLinesById(matchId)).thenReturn(Optional.of(match));
        when(matchProposalRepository.saveAndFlush(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.findWithLinesById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        MatchProposal cancelled = matchLifecycleService.markCancelled(matchId, actorId, "Hủy để đối chiếu lại");

        assertThat(cancelled.status()).isEqualTo(MatchStatus.CANCELLED);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.RECEIVED);
        assertThat(invoice.getLines().get(0).getMatchedQty()).isEqualByComparingTo("0.0000");
    }

    @Test
    void shouldFindActiveMatchAndThrowNotFoundWhenMissing() {
        UUID invoiceId = UUID.randomUUID();
        UUID activeId = UUID.randomUUID();
        UUID cancelledId = UUID.randomUUID();

        Match cancelledMatch = createMatchInStatus(cancelledId, invoiceId, MatchStatus.CANCELLED);
        Match activeMatch = createMatchInStatus(activeId, invoiceId, MatchStatus.PROPOSED);

        when(matchProposalRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId))
                .thenReturn(List.of(cancelledMatch, activeMatch));

        Optional<MatchProposal> found = matchLifecycleService.findActiveMatchByInvoiceId(invoiceId);
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(activeId);

        UUID missingId = UUID.randomUUID();
        when(matchProposalRepository.findWithLinesById(missingId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> matchLifecycleService.getMatch(missingId))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void shouldDeclareTransactionalBoundariesOnAllWriteMethods() throws NoSuchMethodException {
        List<Method> writeMethods = List.of(
                MatchLifecycleServiceImpl.class.getMethod("markProposed", UUID.class),
                MatchLifecycleServiceImpl.class.getMethod("markApproved", UUID.class, UUID.class, String.class),
                MatchLifecycleServiceImpl.class.getMethod("markPosted", UUID.class, UUID.class),
                MatchLifecycleServiceImpl.class.getMethod("markRejected", UUID.class, UUID.class, String.class),
                MatchLifecycleServiceImpl.class.getMethod("markCancelled", UUID.class, UUID.class, String.class)
        );
        for (Method method : writeMethods) {
            Transactional tx = method.getAnnotation(Transactional.class);
            assertThat(tx).as("Method %s must be @Transactional", method.getName()).isNotNull();
            assertThat(tx.readOnly()).isFalse();
        }
    }

    private MatchProposal executeAction(LifecycleAction action, UUID matchId, UUID actorId) {
        return switch (action) {
            case PROPOSE -> matchLifecycleService.markProposed(matchId);
            case APPROVE -> matchLifecycleService.markApproved(matchId, actorId, "Lý do duyệt hợp lệ");
            case POST -> matchLifecycleService.markPosted(matchId, actorId);
            case REJECT -> matchLifecycleService.markRejected(matchId, actorId, "Lý do từ chối hợp lệ");
            case CANCEL -> matchLifecycleService.markCancelled(matchId, actorId, "Lý do hủy hợp lệ");
        };
    }

    private static boolean isMatchTransitionValid(MatchStatus current, LifecycleAction action) {
        return switch (action) {
            case PROPOSE -> current == MatchStatus.PROPOSED;
            case APPROVE -> current.canTransitionTo(MatchStatus.APPROVED);
            case POST -> current.canTransitionTo(MatchStatus.POSTED);
            case REJECT -> current.canTransitionTo(MatchStatus.REJECTED);
            case CANCEL -> current.canTransitionTo(MatchStatus.CANCELLED);
        };
    }

    private static MatchStatus targetMatchStatus(LifecycleAction action) {
        return switch (action) {
            case PROPOSE -> MatchStatus.PROPOSED;
            case APPROVE -> MatchStatus.APPROVED;
            case POST -> MatchStatus.POSTED;
            case REJECT -> MatchStatus.REJECTED;
            case CANCEL -> MatchStatus.CANCELLED;
        };
    }

    private static InvoiceStatus targetInvoiceStatus(LifecycleAction action) {
        return switch (action) {
            case PROPOSE -> InvoiceStatus.MATCHED;
            case APPROVE -> InvoiceStatus.APPROVED;
            case POST -> InvoiceStatus.POSTED;
            case REJECT -> InvoiceStatus.REJECTED;
            case CANCEL -> InvoiceStatus.RECEIVED;
        };
    }

    private Match createMatchInStatus(UUID matchId, UUID invoiceId, MatchStatus status) {
        Match match = new Match(invoiceId, MatchSource.AUTO, null, new BigDecimal("0.9500"), UUID.randomUUID());
        ReflectionTestUtils.setField(match, "id", matchId);
        ReflectionTestUtils.setField(match, "version", 0L);
        return switch (status) {
            case PROPOSED -> match;
            case APPROVED -> matchStateMachine.approve(match, UUID.randomUUID(), "Duyệt mẫu");
            case POSTED -> {
                matchStateMachine.approve(match, UUID.randomUUID(), "Duyệt mẫu");
                yield matchStateMachine.post(match, UUID.randomUUID());
            }
            case REJECTED -> matchStateMachine.reject(match, UUID.randomUUID(), "Từ chối mẫu");
            case CANCELLED -> matchStateMachine.cancel(match, UUID.randomUUID(), "Hủy mẫu");
        };
    }

    private Invoice createInvoiceInStatus(UUID invoiceId, InvoiceStatus status) {
        Invoice invoice = new Invoice(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "1C26TAA",
                "0000123",
                LocalDate.of(2026, 11, 21),
                CurrencyCode.VND,
                UUID.randomUUID()
        );
        invoice.addLine(new InvoiceLine(
                1, UUID.randomUUID(), "ITEM-01", "Hàng mẫu",
                new BigDecimal("10.0000"), new BigDecimal("100000.0000"), new BigDecimal("0.1000")
        ));
        ReflectionTestUtils.setField(invoice, "id", invoiceId);
        ReflectionTestUtils.setField(invoice, "version", 0L);

        switch (status) {
            case RECEIVED -> { /* initial state */ }
            case MATCHING -> invoiceStateMachine.markMatching(invoice);
            case MATCHED -> {
                invoiceStateMachine.markMatching(invoice);
                invoiceStateMachine.markMatched(invoice);
            }
            case EXCEPTION -> {
                invoiceStateMachine.markMatching(invoice);
                invoiceStateMachine.markException(invoice, "Ngoại lệ mẫu");
            }
            case APPROVED -> {
                invoiceStateMachine.markMatching(invoice);
                invoiceStateMachine.markMatched(invoice);
                invoiceStateMachine.markApproved(invoice, UUID.randomUUID(), "Duyệt mẫu");
            }
            case POSTED -> {
                invoiceStateMachine.markMatching(invoice);
                invoiceStateMachine.markMatched(invoice);
                invoiceStateMachine.markApproved(invoice, UUID.randomUUID(), "Duyệt mẫu");
                invoiceStateMachine.markPosted(invoice, UUID.randomUUID());
            }
            case REJECTED -> invoiceStateMachine.markRejected(invoice, "Từ chối mẫu");
            case VOID -> invoiceStateMachine.markVoid(invoice, "Hủy hóa đơn mẫu");
        }
        return invoice;
    }
}
