package com.tmw.backend.posting.service;

import com.tmw.backend.allocation.api.CapacityAllocationService;
import com.tmw.backend.common.enums.CurrencyCode;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.invoice.api.InvoiceStatus;
import com.tmw.backend.matching.api.AllocationState;
import com.tmw.backend.matching.api.MatchLifecycleApi;
import com.tmw.backend.matching.api.MatchProposal;
import com.tmw.backend.matching.api.MatchSource;
import com.tmw.backend.matching.api.MatchStatus;
import com.tmw.backend.posting.api.ActorPrincipal;
import com.tmw.backend.posting.api.PostingResult;
import com.tmw.backend.posting.entity.JournalEntry;
import com.tmw.backend.posting.mapper.PostingMapper;
import com.tmw.backend.posting.repository.JournalEntryRepository;
import com.tmw.backend.posting.sod.SoDGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TWM-41: ApprovalServiceImpl and PostingServiceImpl scaffolding,
 * SoD enforcement, ascending PO line lock ordering, and @Transactional boundaries.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalAndPostingServiceUnitTest {

    private static final UUID USER_MATCHER = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID USER_APPROVER = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final UUID USER_POSTER = UUID.fromString("00000000-0000-0000-0000-000000000033");

    @Mock
    private MatchLifecycleApi matchLifecycleApi;

    @Mock
    private CapacityAllocationService capacityAllocationService;

    @Mock
    private JournalEntryRepository journalEntryRepository;

    private ApprovalServiceImpl approvalService;
    private PostingServiceImpl postingService;

    @BeforeEach
    void setUp() {
        SoDGuard sodGuard = new SoDGuard();
        PostingMapper postingMapper = new PostingMapper();
        approvalService = new ApprovalServiceImpl(matchLifecycleApi, sodGuard, capacityAllocationService);
        postingService = new PostingServiceImpl(
                matchLifecycleApi,
                sodGuard,
                capacityAllocationService,
                journalEntryRepository,
                postingMapper
        );
    }

    @Test
    void shouldApproveWhenActorDiffersFromProposerAndBlockSelfApproval() {
        UUID matchId = UUID.randomUUID();
        MatchProposal proposed = sampleProposal(matchId, MatchStatus.PROPOSED, USER_MATCHER, null, List.of());
        MatchProposal approved = sampleProposal(matchId, MatchStatus.APPROVED, USER_MATCHER, USER_APPROVER, List.of());

        when(matchLifecycleApi.getMatch(matchId)).thenReturn(proposed);
        when(matchLifecycleApi.markApproved(matchId, USER_APPROVER, "Đồng ý duyệt")).thenReturn(approved);

        ActorPrincipal selfMatcher = new ActorPrincipal(USER_MATCHER, "matcher_a", Set.of("ROLE_MATCHER", "ROLE_APPROVER"));
        assertThatThrownBy(() -> approvalService.approve(matchId, selfMatcher, "Tự duyệt"))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SOD_VIOLATION));
        verify(matchLifecycleApi, never()).markApproved(matchId, USER_MATCHER, "Tự duyệt");

        ActorPrincipal approver = new ActorPrincipal(USER_APPROVER, "approver_b", Set.of("ROLE_APPROVER"));
        MatchProposal result = approvalService.approve(matchId, approver, "Đồng ý duyệt");
        assertThat(result.status()).isEqualTo(MatchStatus.APPROVED);
        assertThat(result.approvedBy()).isEqualTo(USER_APPROVER);
    }

    @Test
    void shouldRejectAndReleaseReservedCapacityInAscendingPoLineOrder() {
        UUID matchId = UUID.randomUUID();
        UUID poLineLow = UUID.fromString("00000000-0000-0000-0000-000000000100");
        UUID poLineHigh = UUID.fromString("00000000-0000-0000-0000-000000000200");

        // Deliberately pass lines in descending order to verify ascending deadlock-prevention sort
        List<MatchProposal.MatchLineProposal> lines = List.of(
                sampleLine(poLineHigh, new BigDecimal("2.0000"), new BigDecimal("200000.0000")),
                sampleLine(poLineLow, new BigDecimal("3.0000"), new BigDecimal("300000.0000"))
        );
        MatchProposal proposed = sampleProposal(matchId, MatchStatus.PROPOSED, USER_MATCHER, null, lines);
        MatchProposal rejected = sampleProposal(matchId, MatchStatus.REJECTED, USER_MATCHER, null, lines);

        when(matchLifecycleApi.getMatch(matchId)).thenReturn(proposed);
        when(matchLifecycleApi.markRejected(matchId, USER_APPROVER, "Sai số lượng")).thenReturn(rejected);

        ActorPrincipal approver = new ActorPrincipal(USER_APPROVER, "approver_b", Set.of("ROLE_APPROVER"));

        assertThatThrownBy(() -> approvalService.reject(matchId, approver, "   "))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        MatchProposal result = approvalService.reject(matchId, approver, "Sai số lượng");
        assertThat(result.status()).isEqualTo(MatchStatus.REJECTED);

        InOrder order = inOrder(capacityAllocationService);
        order.verify(capacityAllocationService).release(poLineLow, new BigDecimal("3.0000"), new BigDecimal("300000.0000"));
        order.verify(capacityAllocationService).release(poLineHigh, new BigDecimal("2.0000"), new BigDecimal("200000.0000"));
    }

    @Test
    void shouldPostApprovedMatchConfirmAllocationsInOrderAndCreateJournalEntry() {
        UUID matchId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID poLineLow = UUID.fromString("00000000-0000-0000-0000-000000000100");
        UUID poLineHigh = UUID.fromString("00000000-0000-0000-0000-000000000200");

        List<MatchProposal.MatchLineProposal> lines = List.of(
                sampleLine(poLineHigh, new BigDecimal("4.0000"), new BigDecimal("400000.0000")),
                sampleLine(poLineLow, new BigDecimal("1.0000"), new BigDecimal("100000.0000"))
        );
        MatchProposal approved = sampleProposal(matchId, MatchStatus.APPROVED, USER_MATCHER, USER_APPROVER, lines);
        MatchProposal posted = sampleProposal(matchId, MatchStatus.POSTED, USER_MATCHER, USER_APPROVER, lines);

        when(matchLifecycleApi.getMatch(matchId)).thenReturn(approved);
        when(journalEntryRepository.existsByMatchId(matchId)).thenReturn(false);
        when(matchLifecycleApi.markPosted(matchId, USER_POSTER)).thenReturn(posted);
        when(journalEntryRepository.saveAndFlush(any(JournalEntry.class))).thenAnswer(inv -> {
            JournalEntry entry = inv.getArgument(0);
            ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
            return entry;
        });

        ActorPrincipal poster = new ActorPrincipal(USER_POSTER, "poster_c", Set.of("ROLE_POSTER"));
        PostingCommand command = new PostingCommand(
                supplierId,
                LocalDate.of(2026, 11, 21),
                CurrencyCode.VND,
                new BigDecimal("550000.0000"),
                new BigDecimal("50000.0000"),
                "Ghi sổ hóa đơn NCC"
        );

        PostingResult result = postingService.post(matchId, poster, command);

        assertThat(result.journalEntryId()).isNotNull();
        assertThat(result.entryNumber()).startsWith("JE-20261121-");
        assertThat(result.matchId()).isEqualTo(matchId);
        assertThat(result.matchStatus()).isEqualTo(MatchStatus.POSTED);
        assertThat(result.invoiceStatus()).isEqualTo(InvoiceStatus.POSTED);
        assertThat(result.postedAmount()).isEqualByComparingTo("550000.0000");
        assertThat(result.paymentInstructionId()).isNull();
        assertThat(result.postedBy()).isEqualTo(USER_POSTER);

        InOrder order = inOrder(capacityAllocationService, matchLifecycleApi, journalEntryRepository);
        order.verify(capacityAllocationService).confirm(poLineLow, new BigDecimal("1.0000"), new BigDecimal("100000.0000"));
        order.verify(capacityAllocationService).confirm(poLineHigh, new BigDecimal("4.0000"), new BigDecimal("400000.0000"));
        order.verify(matchLifecycleApi).markPosted(matchId, USER_POSTER);
        order.verify(journalEntryRepository).saveAndFlush(any(JournalEntry.class));
    }

    @Test
    void shouldBlockSoDViolationsInvalidStatusAndDuplicatePosting() {
        UUID matchId = UUID.randomUUID();
        PostingCommand command = new PostingCommand(
                UUID.randomUUID(), LocalDate.now(), CurrencyCode.VND,
                new BigDecimal("1000000"), new BigDecimal("100000"), null
        );

        // 1. Proposer or Approver cannot Post -> SOD_VIOLATION
        MatchProposal approved = sampleProposal(matchId, MatchStatus.APPROVED, USER_MATCHER, USER_APPROVER, List.of());
        when(matchLifecycleApi.getMatch(matchId)).thenReturn(approved);

        ActorPrincipal proposerAsPoster = new ActorPrincipal(USER_MATCHER, "matcher_a", Set.of("ROLE_POSTER"));
        ActorPrincipal approverAsPoster = new ActorPrincipal(USER_APPROVER, "approver_b", Set.of("ROLE_POSTER"));

        assertThatThrownBy(() -> postingService.post(matchId, proposerAsPoster, command))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SOD_VIOLATION));
        assertThatThrownBy(() -> postingService.post(matchId, approverAsPoster, command))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SOD_VIOLATION));

        // 2. Duplicate posting -> DUPLICATE_RESOURCE
        ActorPrincipal validPoster = new ActorPrincipal(USER_POSTER, "poster_c", Set.of("ROLE_POSTER"));
        when(journalEntryRepository.existsByMatchId(matchId)).thenReturn(true);
        assertThatThrownBy(() -> postingService.post(matchId, validPoster, command))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE));

        // 3. Jumping directly from PROPOSED to POSTED -> INVALID_STATE_TRANSITION
        MatchProposal proposed = sampleProposal(matchId, MatchStatus.PROPOSED, USER_MATCHER, null, List.of());
        when(matchLifecycleApi.getMatch(matchId)).thenReturn(proposed);
        assertThatThrownBy(() -> postingService.post(matchId, validPoster, command))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void shouldDeclareTransactionalBoundariesOnApprovalAndPostingServices() throws NoSuchMethodException {
        List<Method> txMethods = List.of(
                ApprovalServiceImpl.class.getMethod("approve", UUID.class, ActorPrincipal.class, String.class),
                ApprovalServiceImpl.class.getMethod("reject", UUID.class, ActorPrincipal.class, String.class),
                PostingServiceImpl.class.getMethod("post", UUID.class, ActorPrincipal.class, PostingCommand.class),
                PostingServiceImpl.class.getMethod("post", UUID.class, ActorPrincipal.class)
        );
        for (Method method : txMethods) {
            Transactional tx = method.getAnnotation(Transactional.class);
            assertThat(tx).as("Method %s.%s must declare @Transactional", method.getDeclaringClass().getSimpleName(), method.getName())
                    .isNotNull();
            assertThat(tx.readOnly()).isFalse();
        }
    }

    private static MatchProposal sampleProposal(
            UUID matchId,
            MatchStatus status,
            UUID proposedBy,
            UUID approvedBy,
            List<MatchProposal.MatchLineProposal> lines
    ) {
        return new MatchProposal(
                matchId,
                UUID.randomUUID(),
                status,
                MatchSource.AUTO,
                AllocationState.RESERVED,
                new BigDecimal("0.9700"),
                UUID.randomUUID(),
                false,
                null,
                proposedBy,
                approvedBy,
                status == MatchStatus.POSTED ? USER_POSTER : null,
                1L,
                lines
        );
    }

    private static MatchProposal.MatchLineProposal sampleLine(UUID poLineId, BigDecimal qty, BigDecimal amount) {
        return new MatchProposal.MatchLineProposal(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                poLineId,
                UUID.randomUUID(),
                qty,
                new BigDecimal("100000.0000"),
                new BigDecimal("100000.0000"),
                amount,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                true
        );
    }
}
