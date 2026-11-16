package com.tmw.backend.posting.security;

import java.util.UUID;

/**
 * Shared validator interface for enforcing Segregation of Duties (SoD).
 * Guarantees that key workflow transitions (Create/Propose -> Approve -> Post)
 * are executed by distinct individuals to prevent fraud and conflicts of interest.
 */
public interface SoDGuard {

    /**
     * Validates that the user attempting to approve a match proposal
     * is not the user who proposed/created it.
     *
     * @param approverId   UUID of the authenticated user performing approval
     * @param proposedById UUID of the user who proposed the match
     * @throws com.tmw.backend.posting.exception.SodViolationException if approverId equals proposedById
     * @throws IllegalArgumentException if any mandatory ID is null
     */
    void validateApprove(UUID approverId, UUID proposedById);

    /**
     * Validates that the user attempting to post/hạch toán a match proposal
     * is neither the proposer nor the approver.
     *
     * @param posterId     UUID of the authenticated user performing posting
     * @param proposedById UUID of the user who proposed the match
     * @param approvedById UUID of the user who approved the match
     * @throws com.tmw.backend.posting.exception.SodViolationException if posterId equals proposedById or approvedById
     * @throws IllegalArgumentException if any mandatory ID is null
     */
    void validatePost(UUID posterId, UUID proposedById, UUID approvedById);
}

