package com.tmw.backend.posting.service;

import com.tmw.backend.posting.api.ActorPrincipal;
import com.tmw.backend.posting.api.PostingResult;

import java.util.UUID;

/**
 * Service interface for accounting posting (ghi sổ kế toán) of an APPROVED match proposal.
 * Endpoints (POST /matches/{matchId}/post) will be wired in Phase 4.
 */
public interface PostingService {

    /**
     * Commits accounting posting for an approved match within a single atomic transaction:
     * enforces dual SoD, confirms PO allocations, transitions Match & Invoice to POSTED,
     * persists an immutable JournalEntry, and returns {@link PostingResult}.
     */
    PostingResult post(UUID matchId, ActorPrincipal actor, PostingCommand command);

    /**
     * Convenience entry point for Phase 4 controller integration once invoice header query API is wired.
     */
    PostingResult post(UUID matchId, ActorPrincipal actor);
}
