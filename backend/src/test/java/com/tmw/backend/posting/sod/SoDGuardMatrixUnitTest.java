package com.tmw.backend.posting.sod;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.matching.api.AllocationState;
import com.tmw.backend.matching.api.MatchProposal;
import com.tmw.backend.matching.api.MatchSource;
import com.tmw.backend.matching.api.MatchStatus;
import com.tmw.backend.posting.api.ActorPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SoDGuardMatrixUnitTest {

    private static final UUID USER_A_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_B_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_C_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private SoDGuard sodGuard;

    @BeforeEach
    void setUp() {
        sodGuard = new SoDGuard();
    }

    static Stream<Arguments> approveMatrixWithMultiRoleActors() {
        ActorPrincipal matcherOnly = new ActorPrincipal(USER_A_ID, "matcher_a", Set.of("ROLE_MATCHER"));
        ActorPrincipal approverOnly = new ActorPrincipal(USER_B_ID, "approver_b", Set.of("ROLE_APPROVER"));
        ActorPrincipal multiMatcherApprover = new ActorPrincipal(
                USER_A_ID, "dual_ma_a", Set.of("ROLE_MATCHER", "ROLE_APPROVER")
        );
        ActorPrincipal multiApproverPoster = new ActorPrincipal(
                USER_B_ID, "dual_ap_b", Set.of("ROLE_APPROVER", "ROLE_POSTER")
        );
        ActorPrincipal superUserAllRolesSelf = new ActorPrincipal(
                USER_A_ID, "super_a", Set.of("ROLE_MATCHER", "ROLE_APPROVER", "ROLE_POSTER")
        );
        ActorPrincipal superUserAllRolesOther = new ActorPrincipal(
                USER_C_ID, "super_c", Set.of("ROLE_MATCHER", "ROLE_APPROVER", "ROLE_POSTER")
        );

        return Stream.of(
                Arguments.of("Người đề xuất A (1 vai) tự duyệt -> VI PHẠM", USER_A_ID, matcherOnly, false),
                Arguments.of("Người dùng A có cả MATCHER + APPROVER tự duyệt đề xuất của mình -> VI PHẠM", USER_A_ID, multiMatcherApprover, false),
                Arguments.of("SuperUser A có đủ 3 vai tự duyệt đề xuất của mình -> VI PHẠM", USER_A_ID, superUserAllRolesSelf, false),
                Arguments.of("Người duyệt B (1 vai) duyệt đề xuất của A -> HỢP LỆ", USER_A_ID, approverOnly, true),
                Arguments.of("Người dùng B có cả APPROVER + POSTER duyệt đề xuất của A -> HỢP LỆ", USER_A_ID, multiApproverPoster, true),
                Arguments.of("SuperUser C có đủ 3 vai duyệt đề xuất của A -> HỢP LỆ", USER_A_ID, superUserAllRolesOther, true)
        );
    }

    @ParameterizedTest(name = "[Approve] {0}")
    @MethodSource("approveMatrixWithMultiRoleActors")
    void shouldEnforceSoDOnApproveAcrossSingleAndMultiRoleUsers(
            String scenario,
            UUID proposedBy,
            ActorPrincipal actor,
            boolean expectedAllowed
    ) {
        MatchProposal match = sampleMatch(proposedBy, null);

        if (expectedAllowed) {
            assertThatCode(() -> sodGuard.checkApprove(match, actor)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> sodGuard.checkApprove(match, actor))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SOD_VIOLATION));
        }
    }

    static Stream<Arguments> postMatrixWithMultiRoleActors() {
        ActorPrincipal userAProposerAndPoster = new ActorPrincipal(
                USER_A_ID, "dual_mp_a", Set.of("ROLE_MATCHER", "ROLE_POSTER")
        );
        ActorPrincipal userBApproverAndPoster = new ActorPrincipal(
                USER_B_ID, "dual_ap_b", Set.of("ROLE_APPROVER", "ROLE_POSTER")
        );
        ActorPrincipal userASuperAllRoles = new ActorPrincipal(
                USER_A_ID, "super_a", Set.of("ROLE_MATCHER", "ROLE_APPROVER", "ROLE_POSTER")
        );
        ActorPrincipal userBSuperAllRoles = new ActorPrincipal(
                USER_B_ID, "super_b", Set.of("ROLE_MATCHER", "ROLE_APPROVER", "ROLE_POSTER")
        );
        ActorPrincipal userCPosterOnly = new ActorPrincipal(
                USER_C_ID, "poster_c", Set.of("ROLE_POSTER")
        );
        ActorPrincipal userCSuperAllRoles = new ActorPrincipal(
                USER_C_ID, "super_c", Set.of("ROLE_MATCHER", "ROLE_APPROVER", "ROLE_POSTER")
        );

        return Stream.of(
                // A đề xuất, B duyệt
                Arguments.of("A (MATCHER+POSTER) cố Post chứng từ do A đề xuất -> VI PHẠM", USER_A_ID, USER_B_ID, userAProposerAndPoster, false),
                Arguments.of("A (đủ 3 vai) cố Post chứng từ do A đề xuất -> VI PHẠM", USER_A_ID, USER_B_ID, userASuperAllRoles, false),
                Arguments.of("B (APPROVER+POSTER) cố Post chứng từ do chính B duyệt -> VI PHẠM", USER_A_ID, USER_B_ID, userBApproverAndPoster, false),
                Arguments.of("B (đủ 3 vai) cố Post chứng từ do chính B duyệt -> VI PHẠM", USER_A_ID, USER_B_ID, userBSuperAllRoles, false),
                Arguments.of("C (chỉ có POSTER) Post chứng từ A đề xuất, B duyệt -> HỢP LỆ", USER_A_ID, USER_B_ID, userCPosterOnly, true),
                Arguments.of("C (đủ 3 vai, nhưng không tham gia đề xuất/duyệt) Post -> HỢP LỆ", USER_A_ID, USER_B_ID, userCSuperAllRoles, true)
        );
    }

    @ParameterizedTest(name = "[Post] {0}")
    @MethodSource("postMatrixWithMultiRoleActors")
    void shouldEnforceSoDOnPostAcrossSingleAndMultiRoleUsers(
            String scenario,
            UUID proposedBy,
            UUID approvedBy,
            ActorPrincipal actor,
            boolean expectedAllowed
    ) {
        MatchProposal match = sampleMatch(proposedBy, approvedBy);

        if (expectedAllowed) {
            assertThatCode(() -> sodGuard.checkPost(match, actor)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> sodGuard.checkPost(match, actor))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SOD_VIOLATION));
        }
    }

    private static MatchProposal sampleMatch(UUID proposedBy, UUID approvedBy) {
        return new MatchProposal(
                UUID.randomUUID(),
                UUID.randomUUID(),
                approvedBy == null ? MatchStatus.PROPOSED : MatchStatus.APPROVED,
                MatchSource.AUTO,
                AllocationState.RESERVED,
                new BigDecimal("0.9500"),
                UUID.randomUUID(),
                false,
                null,
                proposedBy,
                approvedBy,
                null,
                0L,
                List.of()
        );
    }
}