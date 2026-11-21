package com.tmw.backend.invoice.service;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.invoice.api.InvoiceLifecycleApi;
import com.tmw.backend.invoice.api.InvoiceLifecycleSnapshot;
import com.tmw.backend.invoice.entity.Invoice;
import com.tmw.backend.invoice.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.UnaryOperator;

@Service
@RequiredArgsConstructor
public class InvoiceLifecycleServiceImpl implements InvoiceLifecycleApi {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceStateMachine stateMachine;

    @Override
    @Transactional
    public InvoiceLifecycleSnapshot markMatching(UUID invoiceId) {
        return executeTransition(invoiceId, stateMachine::markMatching);
    }

    @Override
    @Transactional
    public InvoiceLifecycleSnapshot markMatched(UUID invoiceId) {
        return executeTransition(invoiceId, stateMachine::markMatched);
    }

    @Override
    @Transactional
    public InvoiceLifecycleSnapshot markException(UUID invoiceId, String reason) {
        return executeTransition(invoiceId, invoice -> stateMachine.markException(invoice, reason));
    }

    @Override
    @Transactional
    public InvoiceLifecycleSnapshot markApproved(UUID invoiceId, UUID approverId, String reason) {
        return executeTransition(invoiceId, invoice -> stateMachine.markApproved(invoice, approverId, reason));
    }

    @Override
    @Transactional
    public InvoiceLifecycleSnapshot markPosted(UUID invoiceId, UUID posterId) {
        return executeTransition(invoiceId, invoice -> stateMachine.markPosted(invoice, posterId));
    }

    @Override
    @Transactional
    public InvoiceLifecycleSnapshot markRejected(UUID invoiceId, String reason) {
        return executeTransition(invoiceId, invoice -> stateMachine.markRejected(invoice, reason));
    }

    @Override
    @Transactional
    public InvoiceLifecycleSnapshot markVoid(UUID invoiceId, String reason) {
        return executeTransition(invoiceId, invoice -> stateMachine.markVoid(invoice, reason));
    }

    @Override
    @Transactional
    public InvoiceLifecycleSnapshot resetToReceived(UUID invoiceId) {
        if (invoiceId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invoiceId không được để trống");
        }
        Invoice invoice = invoiceRepository.findWithLinesById(invoiceId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy hóa đơn: " + invoiceId
                ));

        stateMachine.resetToReceived(invoice);
        Invoice saved = invoiceRepository.saveAndFlush(invoice);
        return toSnapshot(saved);
    }

    private InvoiceLifecycleSnapshot executeTransition(UUID invoiceId, UnaryOperator<Invoice> transitionAction) {
        if (invoiceId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invoiceId không được để trống");
        }
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy hóa đơn: " + invoiceId
                ));

        transitionAction.apply(invoice);
        Invoice saved = invoiceRepository.saveAndFlush(invoice);
        return toSnapshot(saved);
    }

    private static InvoiceLifecycleSnapshot toSnapshot(Invoice invoice) {
        return new InvoiceLifecycleSnapshot(
                invoice.getId(),
                invoice.getStatus(),
                invoice.getVersion(),
                invoice.getApprovedBy(),
                invoice.getPostedBy(),
                invoice.getStatusReason()
        );
    }
}