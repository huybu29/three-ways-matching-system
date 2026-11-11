package com.tmw.backend.document.repository;

import com.tmw.backend.document.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByInvoiceSeriesAndInvoiceNumber(String invoiceSeries, String invoiceNumber);
}