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

    Optional<JournalEntry> findByMatchId(UUID matchId);

    
    Optional<JournalEntry> findByJournalEntryId(String journalEntryId);

   
    boolean existsByMatchId(UUID matchId);

   
    boolean existsByJournalEntryId(String journalEntryId);

    Optional<JournalEntry> findByPaymentInstructionId(String paymentInstructionId);
}

