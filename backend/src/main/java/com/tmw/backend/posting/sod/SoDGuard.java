package com.tmw.backend.posting.sod;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.matching.api.MatchProposal;
import com.tmw.backend.posting.api.ActorPrincipal;

@Component
public class SoDGuard {

    /**
     * Kiểm tra phân tách nhiệm vụ trước khi phê duyệt đề xuất đối chiếu (Match Approval).
     * Người phê duyệt không được trùng với người tạo đề xuất đối chiếu (proposedBy).
     */
    public void checkApprove(MatchProposal proposal, ActorPrincipal actor) {
        validateInputs(proposal, actor);
        UUID actorId = actor.userId();

        if (proposal.proposedBy() != null && proposal.proposedBy().equals(actorId)) {
            throw new BusinessException(
                    ErrorCode.SOD_VIOLATION,
                    "Vi phạm SoD: Người tạo đề xuất đối chiếu không được tự phê duyệt đề xuất của chính mình."
            );
        }
    }

    /**
     * Kiểm tra phân tách nhiệm vụ trước khi ghi sổ bút toán (Journal Posting).
     * Người ghi sổ không được trùng với người tạo đề xuất (proposedBy) hoặc người phê duyệt đối chiếu (approvedBy).
     */
    public void checkPost(MatchProposal proposal, ActorPrincipal actor) {
        validateInputs(proposal, actor);
        UUID actorId = actor.userId();

        if (proposal.proposedBy() != null && proposal.proposedBy().equals(actorId)) {
            throw new BusinessException(
                    ErrorCode.SOD_VIOLATION,
                    "Vi phạm SoD: Người tạo đề xuất đối chiếu không được thực hiện ghi sổ kế toán."
            );
        }
        if (proposal.approvedBy() != null && proposal.approvedBy().equals(actorId)) {
            throw new BusinessException(
                    ErrorCode.SOD_VIOLATION,
                    "Vi phạm SoD: Người phê duyệt đối chiếu không được tự ghi sổ kế toán."
            );
        }
    }

    private static void validateInputs(MatchProposal proposal, ActorPrincipal actor) {
        if (proposal == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "MatchProposal không được để trống khi kiểm tra SoD");
        }
        if (actor == null || actor.userId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ActorPrincipal không hợp lệ khi kiểm tra SoD");
        }
    }
}