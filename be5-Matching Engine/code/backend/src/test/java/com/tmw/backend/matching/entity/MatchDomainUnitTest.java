package com.tmw.backend.matching.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.matching.api.AllocationState;
import com.tmw.backend.matching.api.MatchProposal;
import com.tmw.backend.matching.api.MatchSource;
import com.tmw.backend.matching.api.MatchStatus;
import com.tmw.backend.matching.api.MatchType;
import com.tmw.backend.matching.variance.VarianceResult;

class MatchDomainUnitTest {

    @Test
    void shouldCreateMatchAddLinesMapToMatchProposalAndEnforceStateTransitions() {
        UUID invoiceId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID proposerId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        Match match = new Match(invoiceId, MatchSource.AUTO, policyId, new BigDecimal("0.9250"), proposerId);

        MatchLine line1 = new MatchLine(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                MatchType.THREE_WAY, new BigDecimal("10"), new BigDecimal("50000"), new BigDecimal("50000")
        );
        line1.applyVariance(new VarianceResult(
                new BigDecimal("10.0000"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        ), true);
        line1.assignConfidenceScore(new BigDecimal("1.0000"));

        MatchLine line2 = new MatchLine(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                MatchType.TWO_WAY, new BigDecimal("5"), new BigDecimal("22000"), new BigDecimal("20000")
        );
        line2.applyVariance(new VarianceResult(
                new BigDecimal("5.0000"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("2000.0000"), new BigDecimal("10.0000"), new BigDecimal("10000.0000")
        ), false);
        line2.assignConfidenceScore(new BigDecimal("0.8500"));

        match.addLine(line1);
        match.addLine(line2);

        assertThat(match.getLines()).hasSize(2);
        assertThat(match.isHasVarianceException()).isTrue();
        assertThat(match.getStatus()).isEqualTo(MatchStatus.PROPOSED);
        assertThat(match.getAllocationState()).isEqualTo(AllocationState.NONE);
        // Dòng mới chưa giữ chỗ: cùng trạng thái NONE với Match
        assertThat(match.getLines()).allMatch(l -> l.getAllocationState() == AllocationState.NONE);

        // Không được chốt ALLOCATED khi chưa RESERVED
        assertThatThrownBy(match::markAllocated)
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));

        // NONE -> RESERVED -> ALLOCATED và PROPOSED -> APPROVED
        match.markReserved();
        assertThat(match.getLines()).allMatch(l -> l.getAllocationState() == AllocationState.RESERVED);

        Instant approvedAt = Instant.parse("2026-11-27T02:00:00Z");
        match.updateSummaryReason("Phát hiện lệch đơn giá 10% ở dòng 2");
        match.applyTransition(MatchStatus.APPROVED, approverId, "Đồng ý lệch giá", approvedAt);
        match.markAllocated();
        assertThat(match.getLines()).allMatch(l -> l.getAllocationState() == AllocationState.ALLOCATED);
        assertThat(match.getApprovedAt()).isEqualTo(approvedAt);
        // Lý do duyệt không ghi đè tóm tắt của engine
        assertThat(match.getSummaryReason()).isEqualTo("Phát hiện lệch đơn giá 10% ở dòng 2");

        assertThat(match.getStatus()).isEqualTo(MatchStatus.APPROVED);
        assertThat(match.getApprovedBy()).isEqualTo(approverId);
        assertThat(match.getAllocationState()).isEqualTo(AllocationState.ALLOCATED);

        // Kiểm tra ánh xạ sang API DTO MatchProposal
        MatchProposal proposal = match.toProposal();
        assertThat(proposal.invoiceId()).isEqualTo(invoiceId);
        assertThat(proposal.status()).isEqualTo(MatchStatus.APPROVED);
        assertThat(proposal.approvedBy()).isEqualTo(approverId);
        assertThat(proposal.approvedAt()).isEqualTo(approvedAt);
        assertThat(proposal.allocationState()).isEqualTo(AllocationState.ALLOCATED);
        assertThat(proposal.hasVarianceException()).isTrue();
        assertThat(proposal.lines()).hasSize(2);
    }

    @Test
    void shouldRejectDuplicateInvoiceLineAndPoLinePairWithinSameMatch() {
        Match match = new Match(UUID.randomUUID(), MatchSource.MANUAL, null, null, UUID.randomUUID());
        UUID sharedInvoiceLineId = UUID.randomUUID();
        UUID sharedPoLineId = UUID.randomUUID();
        UUID poId = UUID.randomUUID();

        MatchLine first = new MatchLine(
                sharedInvoiceLineId, poId, sharedPoLineId, null,
                MatchType.TWO_WAY, new BigDecimal("5"), new BigDecimal("10000"), new BigDecimal("10000")
        );
        MatchLine duplicatePair = new MatchLine(
                sharedInvoiceLineId, poId, sharedPoLineId, null,
                MatchType.TWO_WAY, new BigDecimal("3"), new BigDecimal("10000"), new BigDecimal("10000")
        );

        match.addLine(first);

        assertThatThrownBy(() -> match.addLine(duplicatePair))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.0001", "-15"})
    void shouldRejectNonPositiveMatchedQtyOnMatchLine(String invalidQty) {
        assertThatThrownBy(() -> new MatchLine(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                MatchType.TWO_WAY, new BigDecimal(invalidQty), BigDecimal.TEN, BigDecimal.TEN
        )).isInstanceOfSatisfying(BusinessException.class, ex ->
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void shouldRequireGrnLineIdForThreeWayMatchAndBlockTransitionsFromImmutableState() {
        assertThatThrownBy(() -> new MatchLine(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                MatchType.THREE_WAY, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN
        )).isInstanceOfSatisfying(BusinessException.class, ex ->
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        // Luồng từ chối và giải phóng hạn mức
        Match rejectedMatch = new Match(UUID.randomUUID(), MatchSource.AUTO, null, BigDecimal.ONE, UUID.randomUUID());
        rejectedMatch.markReserved();
        rejectedMatch.applyTransition(MatchStatus.REJECTED, UUID.randomUUID(), "Từ chối đề xuất");
        rejectedMatch.markReleased();

        assertThat(rejectedMatch.getStatus()).isEqualTo(MatchStatus.REJECTED);
        assertThat(rejectedMatch.getRejectReason()).isEqualTo("Từ chối đề xuất");
        assertThat(rejectedMatch.getSummaryReason()).isNull();
        assertThat(rejectedMatch.getAllocationState()).isEqualTo(AllocationState.RELEASED);

        // Luồng POSTED bất biến không cho phép chỉnh sửa hay đổi trạng thái
        Match postedMatch = new Match(UUID.randomUUID(), MatchSource.AUTO, null, BigDecimal.ONE, UUID.randomUUID());
        postedMatch.markReserved();
        postedMatch.applyTransition(MatchStatus.APPROVED, UUID.randomUUID(), "Duyệt đối chiếu");
        postedMatch.markAllocated();
        postedMatch.applyTransition(MatchStatus.POSTED, UUID.randomUUID(), "Ghi sổ kế toán");

        assertThatThrownBy(() -> postedMatch.applyTransition(MatchStatus.APPROVED, UUID.randomUUID(), "Không được phép"))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        assertThatThrownBy(postedMatch::markReleased)
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void shouldKeepConfidenceNullForManualAndRequireItForAuto() {
        Match manual = new Match(UUID.randomUUID(), MatchSource.MANUAL, null, null, UUID.randomUUID());
        assertThat(manual.getOverallConfidence()).isNull();
        assertThat(manual.toProposal().overallConfidence()).isNull();

        assertThatThrownBy(() -> new Match(UUID.randomUUID(), MatchSource.MANUAL, null, new BigDecimal("0.8000"), UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> new Match(UUID.randomUUID(), MatchSource.AUTO, null, null, UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> new Match(UUID.randomUUID(), MatchSource.AUTO, null, new BigDecimal("1.0001"), UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void shouldKeepMatchAndLineAllocationStateInSyncAndBlockAddingLinesAfterReservation() {
        Match match = new Match(UUID.randomUUID(), MatchSource.AUTO, null, new BigDecimal("0.9000"), UUID.randomUUID());
        match.addLine(new MatchLine(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                MatchType.TWO_WAY, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN
        ));
        match.markReserved();

        assertThatThrownBy(() -> match.addLine(new MatchLine(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                MatchType.TWO_WAY, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN
        ))).isInstanceOfSatisfying(BusinessException.class, ex ->
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));

        match.applyTransition(MatchStatus.CANCELLED, UUID.randomUUID(), "Hủy để ghép lại");
        match.markReleased();
        assertThat(match.getAllocationState()).isEqualTo(AllocationState.RELEASED);
        assertThat(match.getLines()).allMatch(l -> l.getAllocationState() == AllocationState.RELEASED);
        assertThat(match.getRejectReason()).isEqualTo("Hủy để ghép lại");
    }

    @Test
    void shouldRequireReasonForRejectAndCancel() {
        Match match = new Match(UUID.randomUUID(), MatchSource.AUTO, null, BigDecimal.ONE, UUID.randomUUID());
        assertThatThrownBy(() -> match.applyTransition(MatchStatus.REJECTED, UUID.randomUUID(), "  "))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThat(match.getStatus()).isEqualTo(MatchStatus.PROPOSED);
    }
}
