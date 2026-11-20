package com.tmw.backend.matching.api;

import java.util.Optional;
import java.util.UUID;

public interface MatchLifecycleApi {

    MatchProposal getMatch(UUID matchId);

    Optional<MatchProposal> findActiveMatchByInvoiceId(UUID invoiceId);

    MatchProposal markApproved(UUID matchId, UUID approverId, String reason);

    MatchProposal markPosted(UUID matchId, UUID posterId);

    MatchProposal markRejected(UUID matchId, UUID actorId, String reason);

    MatchProposal markCancelled(UUID matchId, UUID actorId, String reason);
}