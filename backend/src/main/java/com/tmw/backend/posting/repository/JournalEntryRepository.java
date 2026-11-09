package com.tmw.backend.posting.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tmw.backend.posting.entity.JournalEntry;

/**
 * Repository for managing {@link JournalEntry} entities.
 * Note: Journal entries are immutable. Only insert and query operations are supported.
 */
@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    /**
     * Finds a journal entry associated with a specific match proposal.
     *
     * @param matchId UUID of the match proposal
     * @return Optional containing the journal entry if found
     */
    Optional<JournalEntry> findByMatchId(UUID matchId);

    /**
     * Finds a journal entry by its business identifier (journal_entry_id).
     *
     * @param journalEntryId unique business identifier (e.g. JE-202611-0001)
     * @return Optional containing the journal entry if found
     */
    Optional<JournalEntry> findByJournalEntryId(String journalEntryId);

    /**
     * Checks if a journal entry already exists for a given match proposal.
     *
     * @param matchId UUID of the match proposal
     * @return true if already posted, false otherwise
     */
    boolean existsByMatchId(UUID matchId);

    /**
     * Checks if a journal entry with the given business id exists.
     *
     * @param journalEntryId unique business identifier
     * @return true if exists, false otherwise
     */
    boolean existsByJournalEntryId(String journalEntryId);

    /**
     * Finds a journal entry by its payment instruction reference ID.
     *
     * @param paymentInstructionId payment instruction ID
     * @return Optional containing the journal entry if found
     */
    Optional<JournalEntry> findByPaymentInstructionId(String paymentInstructionId);
}

