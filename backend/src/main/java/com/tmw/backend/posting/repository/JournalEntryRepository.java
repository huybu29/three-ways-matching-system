package com.tmw.backend.posting.repository;

import com.tmw.backend.posting.entity.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing {@link JournalEntry} entities.
 * Journal entries are accounting records; updates/deletes are forbidden by domain policy.
 */
@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    Optional<JournalEntry> findByMatchId(UUID matchId);

    Optional<JournalEntry> findByInvoiceId(UUID invoiceId);

    Optional<JournalEntry> findByEntryNumber(String entryNumber);

    Optional<JournalEntry> findByPaymentInstructionId(UUID paymentInstructionId);

    List<JournalEntry> findBySupplierIdOrderByPostedAtDesc(UUID supplierId);

    List<JournalEntry> findByPostingDateBetweenOrderByPostingDateAsc(LocalDate fromDate, LocalDate toDate);

    boolean existsByMatchId(UUID matchId);

    boolean existsByInvoiceId(UUID invoiceId);

    boolean existsByEntryNumber(String entryNumber);
}
