package com.tmw.backend.posting.service;

import com.tmw.backend.matching.api.MatchProposal;
import com.tmw.backend.posting.api.ActorPrincipal;

import java.util.UUID;

/**
 * Service interface for approving or rejecting a {@link MatchProposal}
 * under Segregation of Duties (SoD) enforcement.
 * Endpoints (POST /matches/{matchId}/approve, /reject) will be wired in Phase 4.
 */
public interface ApprovalService {

    /**
     * Approves a proposed match after verifying SoD and approval preconditions.
     * Transitions Match to APPROVED and Invoice to APPROVED within a single transaction.
     */
    MatchProposal approve(UUID matchId, ActorPrincipal actor, String comment);

    /**
     * Rejects a match proposal with a mandatory reason, releases any reserved PO capacity,
     * and transitions both Match and Invoice to REJECTED within a single transaction.
     */
    MatchProposal reject(UUID matchId, ActorPrincipal actor, String reason);
}
