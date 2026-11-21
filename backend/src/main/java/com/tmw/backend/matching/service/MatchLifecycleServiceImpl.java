package com.tmw.backend.matching.service;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.invoice.api.InvoiceLifecycleApi;
import com.tmw.backend.matching.api.MatchLifecycleApi;
import com.tmw.backend.matching.api.MatchProposal;
import com.tmw.backend.matching.api.MatchStatus;
import com.tmw.backend.matching.entity.Match;
import com.tmw.backend.matching.repository.MatchProposalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link MatchLifecycleApi} owned by BE-6.
 * Coordinates Match state transitions via {@link MatchStateMachine} and synchronizes
 * the associated Invoice lifecycle via {@link InvoiceLifecycleApi} in the same DB transaction.
 *
 * State mapping:
 * - Match PROPOSED  -> Invoice MATCHED
 * - Match APPROVED  -> Invoice APPROVED
 * - Match POSTED    -> Invoice POSTED
 * - Match REJECTED  -> Invoice REJECTED
 * - Match CANCELLED -> Invoice RECEIVED (reset for rematching)
 */
@Service
@RequiredArgsConstructor
public class MatchLifecycleServiceImpl implements MatchLifecycleApi {

    private final MatchProposalRepository matchProposalRepository;
    private final MatchStateMachine matchStateMachine;
    private final InvoiceLifecycleApi invoiceLifecycleApi;

    @Override
    @Transactional(readOnly = true)
    public MatchProposal getMatch(UUID matchId) {
        return loadMatchWithLines(matchId).toProposal();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MatchProposal> findActiveMatchByInvoiceId(UUID invoiceId) {
        if (invoiceId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invoiceId không được để trống");
        }
        return matchProposalRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId).stream()
                .filter(match -> match.getStatus() != null && match.getStatus().isActive())
                .findFirst()
                .map(Match::toProposal);
    }

    /**
     * Synchronizes a newly created PROPOSED match with its Invoice (transitioning Invoice to MATCHED)
     * within the same transaction.
     */
    @Transactional(rollbackFor = Exception.class)
    public MatchProposal markProposed(UUID matchId) {
        Match match = loadMatchWithLines(matchId);
        if (match.getStatus() != MatchStatus.PROPOSED) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    String.format(
                            "Không thể đồng bộ trạng thái PROPOSED cho Match %s đang ở trạng thái %s",
                            matchId, match.getStatus()
                    )
            );
        }

        invoiceLifecycleApi.markMatched(match.getInvoiceId());
        return match.toProposal();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MatchProposal markApproved(UUID matchId, UUID approverId, String reason) {
        Match match = loadMatchWithLines(matchId);
        matchStateMachine.approve(match, approverId, reason);
        Match saved = matchProposalRepository.saveAndFlush(match);

        invoiceLifecycleApi.markApproved(saved.getInvoiceId(), approverId, reason);
        return saved.toProposal();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MatchProposal markPosted(UUID matchId, UUID posterId) {
        Match match = loadMatchWithLines(matchId);
        matchStateMachine.post(match, posterId);
        Match saved = matchProposalRepository.saveAndFlush(match);

        invoiceLifecycleApi.markPosted(saved.getInvoiceId(), posterId);
        return saved.toProposal();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MatchProposal markRejected(UUID matchId, UUID actorId, String reason) {
        Match match = loadMatchWithLines(matchId);
        matchStateMachine.reject(match, actorId, reason);
        Match saved = matchProposalRepository.saveAndFlush(match);

        invoiceLifecycleApi.markRejected(saved.getInvoiceId(), reason);
        return saved.toProposal();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MatchProposal markCancelled(UUID matchId, UUID actorId, String reason) {
        Match match = loadMatchWithLines(matchId);
        matchStateMachine.cancel(match, actorId, reason);
        Match saved = matchProposalRepository.saveAndFlush(match);

        invoiceLifecycleApi.resetToReceived(saved.getInvoiceId());
        return saved.toProposal();
    }

    private Match loadMatchWithLines(UUID matchId) {
        if (matchId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "matchId không được để trống");
        }
        return matchProposalRepository.findWithLinesById(matchId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "Không tìm thấy đề xuất đối chiếu (Match): " + matchId
                ));
    }
}
