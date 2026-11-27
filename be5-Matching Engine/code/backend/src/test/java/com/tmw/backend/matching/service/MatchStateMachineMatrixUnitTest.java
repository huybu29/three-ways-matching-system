package com.tmw.backend.matching.service;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.matching.api.MatchSource;
import com.tmw.backend.matching.api.MatchStatus;
import com.tmw.backend.matching.entity.Match;
import com.tmw.backend.matching.entity.MatchLine;
import com.tmw.backend.matching.api.MatchType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchStateMachineMatrixUnitTest {

    private static final Map<MatchStatus, Set<MatchStatus>> EXPECTED_MATRIX = Map.of(
            MatchStatus.PROPOSED, Set.of(MatchStatus.APPROVED, MatchStatus.REJECTED, MatchStatus.CANCELLED),
            MatchStatus.APPROVED, Set.of(MatchStatus.POSTED, MatchStatus.REJECTED, MatchStatus.CANCELLED),
            MatchStatus.POSTED, Set.of(),
            MatchStatus.REJECTED, Set.of(),
            MatchStatus.CANCELLED, Set.of()
    );

    private MatchStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new MatchStateMachine();
    }

    static Stream<Arguments> all25MatchStatePairs() {
        List<Arguments> pairs = new ArrayList<>();
        for (MatchStatus source : MatchStatus.values()) {
            for (MatchStatus target : MatchStatus.values()) {
                pairs.add(Arguments.of(source, target, EXPECTED_MATRIX.get(source).contains(target)));
            }
        }
        return pairs.stream();
    }

    @ParameterizedTest(name = "{0} -> {1} (hợp lệ = {2})")
    @MethodSource("all25MatchStatePairs")
    void shouldEnforceFull5x5MatchStateTransitionMatrix(
            MatchStatus source,
            MatchStatus target,
            boolean expectedAllowed
    ) {
        Match match = createMatchInStatus(source);
        UUID actorId = UUID.randomUUID();

        if (expectedAllowed) {
            Match updated = stateMachine.transition(match, target, actorId, "Lý do hợp lệ");
            assertThat(updated.getStatus()).isEqualTo(target);
        } else {
            assertThatThrownBy(() -> stateMachine.transition(match, target, actorId, "Lý do hợp lệ"))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
            assertThat(match.getStatus()).isEqualTo(source);
        }
    }

    @Test
    void shouldKeepPostedMatchStrictlyImmutable() {
        Match postedMatch = createMatchInStatus(MatchStatus.POSTED);
        UUID newActor = UUID.randomUUID();

        // Không thể chuyển sang bất kỳ trạng thái nào khác
        for (MatchStatus target : MatchStatus.values()) {
            assertThatThrownBy(() -> stateMachine.transition(postedMatch, target, newActor, "Thử sửa sau post"))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }

        // Không thể thêm dòng mới vào Match đã POSTED
        MatchLine extraLine = new MatchLine(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                MatchType.TWO_WAY, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN
        );
        assertThatThrownBy(() -> postedMatch.addLine(extraLine))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    private Match createMatchInStatus(MatchStatus status) {
        UUID proposer = UUID.randomUUID();
        Match match = new Match(UUID.randomUUID(), MatchSource.AUTO, null, new BigDecimal("0.9500"), proposer);
        return switch (status) {
            case PROPOSED -> match;
            case APPROVED -> stateMachine.approve(match, UUID.randomUUID(), null);
            case POSTED -> {
                stateMachine.approve(match, UUID.randomUUID(), null);
                yield stateMachine.post(match, UUID.randomUUID());
            }
            case REJECTED -> stateMachine.reject(match, UUID.randomUUID(), "Từ chối đề xuất");
            case CANCELLED -> stateMachine.cancel(match, UUID.randomUUID(), "Hủy lượt đối chiếu");
        };
    }

    @Test
    void shouldStampApprovedAtAndPostedAtFromClock() {
        Instant fixed = Instant.parse("2026-11-27T03:15:00Z");
        MatchStateMachine fixedClockMachine = new MatchStateMachine(Clock.fixed(fixed, ZoneOffset.UTC));
        Match match = new Match(UUID.randomUUID(), MatchSource.AUTO, null, new BigDecimal("0.9500"), UUID.randomUUID());

        fixedClockMachine.approve(match, UUID.randomUUID(), null);
        fixedClockMachine.post(match, UUID.randomUUID());

        assertThat(match.getApprovedAt()).isEqualTo(fixed);
        assertThat(match.getPostedAt()).isEqualTo(fixed);
    }
}
