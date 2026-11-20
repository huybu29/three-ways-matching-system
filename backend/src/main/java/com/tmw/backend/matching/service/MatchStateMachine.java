package com.tmw.backend.matching.service;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.matching.api.MatchStatus;
import com.tmw.backend.matching.entity.Match;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class MatchStateMachine {

    public Match transition(Match match, MatchStatus targetStatus, UUID actorId, String reason) {
        if (match == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Bản ghi đối chiếu (Match) không tồn tại");
        }
        if (targetStatus == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Trạng thái đích của Match không được để trống");
        }

        MatchStatus currentStatus = match.getStatus();
        if (currentStatus == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Match chưa có trạng thái khởi tạo");
        }

        if (currentStatus.isImmutable()) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    String.format("Match %s đã ở trạng thái POSTED nên bất biến, không thể chuyển sang %s",
                            match.getId(), targetStatus)
            );
        }

        if (!currentStatus.canTransitionTo(targetStatus)) {
            Set<MatchStatus> allowed = currentStatus.allowedNextStates();
            String allowedDesc = allowed.isEmpty() ? "không có (trạng thái kết thúc)" : allowed.toString();
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    String.format(
                            "Không thể chuyển trạng thái Match từ %s sang %s. Trạng thái hợp lệ tiếp theo: %s",
                            currentStatus, targetStatus, allowedDesc
                    )
            );
        }

        validateGuards(targetStatus, actorId, reason);

        match.applyTransition(targetStatus, actorId, reason);
        return match;
    }

    public Match approve(Match match, UUID approverId, String reason) {
        return transition(match, MatchStatus.APPROVED, approverId, reason);
    }

    public Match post(Match match, UUID posterId) {
        return transition(match, MatchStatus.POSTED, posterId, null);
    }

    public Match reject(Match match, UUID actorId, String reason) {
        return transition(match, MatchStatus.REJECTED, actorId, reason);
    }

    public Match cancel(Match match, UUID actorId, String reason) {
        return transition(match, MatchStatus.CANCELLED, actorId, reason);
    }

    private void validateGuards(MatchStatus targetStatus, UUID actorId, String reason) {
        if (actorId == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Bắt buộc xác định người thực hiện (actorId) khi chuyển Match sang " + targetStatus
            );
        }
        if (targetStatus.requiresReason() && (reason == null || reason.isBlank())) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Bắt buộc nhập lý do (reason) khi chuyển Match sang trạng thái " + targetStatus
            );
        }
    }
}