package com.tmw.backend.posting.security;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.tmw.backend.posting.exception.SodViolationException;

import lombok.extern.slf4j.Slf4j;

/**
 * Default implementation of {@link SoDGuard}.
 * Enforces Segregation of Duties (SoD) by validating that actors performing
 * approval and posting on a match proposal do not overlap with previous steps.
 */
@Slf4j
@Component
public class DefaultSoDGuard implements SoDGuard {

    @Override
    public void validateApprove(UUID approverId, UUID proposedById) {
        Objects.requireNonNull(approverId, "Approver ID cannot be null");
        Objects.requireNonNull(proposedById, "Proposed-by ID cannot be null");

        if (approverId.equals(proposedById)) {
            log.warn("SoD Violation on APPROVE: approver {} is the same as proposer {}", approverId, proposedById);
            throw new SodViolationException(
                "Segregation of Duties violation: Approver cannot be the same user who proposed the match (User ID: " + approverId + ")",
                approverId,
                proposedById,
                "APPROVE"
            );
        }
    }

    @Override
    public void validatePost(UUID posterId, UUID proposedById, UUID approvedById) {
        Objects.requireNonNull(posterId, "Poster ID cannot be null");
        Objects.requireNonNull(proposedById, "Proposed-by ID cannot be null");

        if (posterId.equals(proposedById)) {
            log.warn("SoD Violation on POST: poster {} is the same as proposer {}", posterId, proposedById);
            throw new SodViolationException(
                "Segregation of Duties violation: Poster cannot be the same user who proposed the match (User ID: " + posterId + ")",
                posterId,
                proposedById,
                "POST"
            );
        }

        if (approvedById != null && posterId.equals(approvedById)) {
            log.warn("SoD Violation on POST: poster {} is the same as approver {}", posterId, approvedById);
            throw new SodViolationException(
                "Segregation of Duties violation: Poster cannot be the same user who approved the match (User ID: " + posterId + ")",
                posterId,
                approvedById,
                "POST"
            );
        }
    }
}

