package com.tmw.backend.posting.service;

import com.tmw.backend.allocation.api.CapacityAllocationService;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.matching.api.AllocationState;
import com.tmw.backend.matching.api.MatchLifecycleApi;
import com.tmw.backend.matching.api.MatchProposal;
import com.tmw.backend.matching.api.MatchStatus;
import com.tmw.backend.posting.api.ActorPrincipal;
import com.tmw.backend.posting.sod.SoDGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Scaffold implementation of {@link ApprovalService} defining clear transaction boundaries,
 * SoD validation, and lifecycle/capacity coordination.
 * Phase 3/4 will plug in ApprovalPreconditionChecker (open MatchException check), If-Match, and @Audited.
 */
@Service
@RequiredArgsConstructor
public class ApprovalServiceImpl implements ApprovalService {

    private final MatchLifecycleApi matchLifecycleApi;
    private final SoDGuard sodGuard;
    private final CapacityAllocationService capacityAllocationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MatchProposal approve(UUID matchId, ActorPrincipal actor, String comment) {
        validateRequest(matchId, actor);

        MatchProposal proposal = matchLifecycleApi.getMatch(matchId);
        sodGuard.checkApprove(proposal, actor);
        validateApprovalPreconditions(proposal);

        return matchLifecycleApi.markApproved(matchId, actor.userId(), comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MatchProposal reject(UUID matchId, ActorPrincipal actor, String reason) {
        validateRequest(matchId, actor);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Bắt buộc nhập lý do từ chối (reason) khi reject đề xuất đối chiếu"
            );
        }

        MatchProposal proposal = matchLifecycleApi.getMatch(matchId);
        MatchProposal rejected = matchLifecycleApi.markRejected(matchId, actor.userId(), reason);

        releaseReservedAllocations(proposal);
        return rejected;
    }

    /**
     * Hook for Phase 3 ApprovalPreconditionChecker (e.g., blocking approval if open MatchException exists).
     */
    protected void validateApprovalPreconditions(MatchProposal proposal) {
        if (proposal.status() != MatchStatus.PROPOSED) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    String.format(
                            "Chỉ có thể phê duyệt Match đang ở trạng thái PROPOSED (hiện tại: %s)",
                            proposal.status()
                    )
            );
        }
    }

    private void releaseReservedAllocations(MatchProposal proposal) {
        if (proposal.allocationState() != AllocationState.RESERVED
                || proposal.lines() == null
                || proposal.lines().isEmpty()) {
            return;
        }

        // Khóa dòng po_line_capacity theo po_line_id tăng dần để chống deadlock
        List<MatchProposal.MatchLineProposal> sortedLines = proposal.lines().stream()
                .filter(line -> line.poLineId() != null)
                .sorted(Comparator.comparing(MatchProposal.MatchLineProposal::poLineId))
                .toList();

        for (MatchProposal.MatchLineProposal line : sortedLines) {
            capacityAllocationService.release(line.poLineId(), line.matchedQty(), line.matchedAmount());
        }
    }

    private static void validateRequest(UUID matchId, ActorPrincipal actor) {
        if (matchId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "matchId không được để trống");
        }
        if (actor == null || actor.userId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thông tin người thực hiện (actor) không hợp lệ");
        }
    }
}
