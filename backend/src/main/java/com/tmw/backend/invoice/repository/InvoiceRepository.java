package com.tmw.backend.invoice.repository;

import com.tmw.backend.invoice.entity.Invoice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @EntityGraph(attributePaths = "lines")
    Optional<Invoice> findWithLinesById(UUID id);
}