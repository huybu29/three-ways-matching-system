package com.tmw.backend.matching.entity;

import com.tmw.backend.matching.exception.InvalidStateTransitionException;
import com.tmw.backend.matching.variance.VarianceCalculator;
import com.tmw.backend.matching.variance.VarianceInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchProposalLifecycleTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);
    private final UUID matcher = UUID.randomUUID();
    private final UUID approver = UUID.randomUUID();
    private final UUID poster = UUID.randomUUID();

    private MatchProposal proposal;

    @BeforeEach
    void setUp() {
        proposal = MatchProposal.manual(UUID.randomUUID(), UUID.randomUUID(), matcher, clock);
        proposal.addLine(line("10", "100"));
        proposal.addLine(line("5", "20"));
    }

    private static MatchLine line(String qty, String price) {
        return new MatchLine(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                MatchType.THREE_WAY, new BigDecimal(qty), new BigDecimal(price), new BigDecimal(price));
    }

    // ---------------------------------------------------------------- MatchStatus enum

    @Test
    void statusTransitionTable() {
        assertThat(MatchStatus.PROPOSED.allowedNext())
                .containsExactlyInAnyOrder(MatchStatus.APPROVED, MatchStatus.REJECTED, MatchStatus.CANCELLED);
        assertThat(MatchStatus.APPROVED.allowedNext()).containsExactly(MatchStatus.POSTED);
        assertThat(MatchStatus.POSTED.isTerminal()).isTrue();
        assertThat(MatchStatus.REJECTED.isTerminal()).isTrue();
        assertThat(MatchStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(MatchStatus.PROPOSED.canTransitionTo(MatchStatus.POSTED)).isFalse();
        assertThat(MatchStatus.PROPOSED.canTransitionTo(null)).isFalse();
    }

    @Test
    void holdsReservation_onlyWhileActive() {
        assertThat(MatchStatus.PROPOSED.holdsReservation()).isTrue();
        assertThat(MatchStatus.APPROVED.holdsReservation()).isTrue();
        assertThat(MatchStatus.POSTED.holdsReservation()).isFalse();
        assertThat(MatchStatus.REJECTED.holdsReservation()).isFalse();
        assertThat(MatchStatus.CANCELLED.holdsReservation()).isFalse();
    }

    // ---------------------------------------------------------------- creation

    @Test
    void newProposal_isProposedWithLinesReservedAndTotal() {
        assertThat(proposal.getStatus()).isEqualTo(MatchStatus.PROPOSED);
        assertThat(proposal.getSource()).isEqualTo(MatchSource.MANUAL);
        assertThat(proposal.getConfidence()).isNull();
        assertThat(proposal.getLines()).extracting(MatchLine::getLineNo).containsExactly(1, 2);
        assertThat(proposal.getLines()).allMatch(l -> l.getStatus() == MatchLineStatus.RESERVED);
        assertThat(proposal.getTotalMatchedAmount()).isEqualByComparingTo("1100"); // 10*100 + 5*20
        assertThat(proposal.getProposedAt()).isEqualTo(clock.instant());
    }

    @Test
    void autoProposal_requiresConfidenceInRange() {
        var p = MatchProposal.auto(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("0.87654"), matcher, clock);
        assertThat(p.getConfidence()).isEqualByComparingTo("0.8765");

        assertThatThrownBy(() -> MatchProposal.auto(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.01"), matcher, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MatchProposal.auto(UUID.randomUUID(), UUID.randomUUID(), null, matcher, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void threeWayLine_requiresGrn() {
        assertThatThrownBy(() -> new MatchLine(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                MatchType.THREE_WAY, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------- happy path

    @Test
    void approveThenPost_allocatesAllLines() {
        proposal.approve(approver, clock);
        assertThat(proposal.getStatus()).isEqualTo(MatchStatus.APPROVED);
        assertThat(proposal.getApprovedBy()).isEqualTo(approver);

        proposal.post(poster, clock);
        assertThat(proposal.getStatus()).isEqualTo(MatchStatus.POSTED);
        assertThat(proposal.getPostedBy()).isEqualTo(poster);
        assertThat(proposal.getLines()).allMatch(l -> l.getStatus() == MatchLineStatus.ALLOCATED);
    }

    @Test
    void reject_releasesLinesAndKeepsReason() {
        proposal.reject(approver, "  Sai đơn giá  ", clock);

        assertThat(proposal.getStatus()).isEqualTo(MatchStatus.REJECTED);
        assertThat(proposal.getReason()).isEqualTo("Sai đơn giá");
        assertThat(proposal.getLines()).allMatch(l -> l.getStatus() == MatchLineStatus.RELEASED);
    }

    @Test
    void reject_requiresReason() {
        assertThatThrownBy(() -> proposal.reject(approver, " ", clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(proposal.getStatus()).isEqualTo(MatchStatus.PROPOSED);
    }

    @Test
    void cancel_releasesLines() {
        proposal.cancel(matcher, null, clock);
        assertThat(proposal.getStatus()).isEqualTo(MatchStatus.CANCELLED);
        assertThat(proposal.getLines()).allMatch(l -> l.getStatus() == MatchLineStatus.RELEASED);
    }

    // ---------------------------------------------------------------- invalid transitions

    @Test
    void cannotPostWithoutApproval() {
        assertThatThrownBy(() -> proposal.post(poster, clock))
                .isInstanceOf(InvalidStateTransitionException.class)
                .satisfies(e -> {
                    var ex = (InvalidStateTransitionException) e;
                    assertThat(ex.getCode()).isEqualTo("INVALID_STATE_TRANSITION");
                    assertThat(ex.getFrom()).isEqualTo(MatchStatus.PROPOSED);
                    assertThat(ex.getTo()).isEqualTo(MatchStatus.POSTED);
                });
        assertThat(proposal.getLines()).allMatch(l -> l.getStatus() == MatchLineStatus.RESERVED);
    }

    @Test
    void cannotCancelOrRejectAfterApproval() {
        proposal.approve(approver, clock);
        assertThatThrownBy(() -> proposal.cancel(matcher, "x", clock))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> proposal.reject(approver, "x", clock))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(value = MatchStatus.class, names = {"POSTED", "REJECTED", "CANCELLED"})
    void terminalStates_blockEverything(MatchStatus terminal) {
        switch (terminal) {
            case POSTED -> { proposal.approve(approver, clock); proposal.post(poster, clock); }
            case REJECTED -> proposal.reject(approver, "r", clock);
            case CANCELLED -> proposal.cancel(matcher, null, clock);
            default -> throw new IllegalStateException();
        }
        assertThatThrownBy(() -> proposal.approve(approver, clock)).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> proposal.post(poster, clock)).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> proposal.cancel(matcher, null, clock)).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> proposal.addLine(line("1", "1"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotApproveEmptyProposal() {
        var empty = MatchProposal.manual(UUID.randomUUID(), UUID.randomUUID(), matcher, clock);
        assertThatThrownBy(() -> empty.approve(approver, clock)).isInstanceOf(IllegalStateException.class);
    }

    // ---------------------------------------------------------------- variance on line

    @Test
    void applyVariance_copiesResultAndResetsTolerance() {
        MatchLine l = proposal.getLines().get(0);
        l.markTolerance(true);

        var r = new VarianceCalculator().calculate(
                VarianceInput.threeWay(new BigDecimal("10"), new BigDecimal("102"), new BigDecimal("100"), new BigDecimal("9")));
        l.applyVariance(r);

        assertThat(l.getQtyVariance()).isEqualByComparingTo("1");
        assertThat(l.getPriceVariancePct()).isEqualByComparingTo("2");
        assertThat(l.getAmountVariance()).isEqualByComparingTo("120"); // 1020 - 900
        assertThat(l.getWithinTolerance()).isNull();
        assertThat(proposal.allLinesWithinTolerance()).isFalse();
    }
}
