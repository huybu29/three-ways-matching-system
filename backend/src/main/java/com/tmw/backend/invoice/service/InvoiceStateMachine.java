package com.tmw.backend.invoice.service;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.invoice.api.InvoiceStatus;
import com.tmw.backend.invoice.entity.Invoice;
import com.tmw.backend.invoice.entity.InvoiceLine;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class InvoiceStateMachine {

    public Invoice transition(Invoice invoice, InvoiceStatus targetStatus, String reason) {
        if (invoice == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Hóa đơn không tồn tại");
        }
        if (targetStatus == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Trạng thái đích không được để trống");
        }

        InvoiceStatus currentStatus = invoice.getStatus();
        if (currentStatus == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Hóa đơn chưa có trạng thái khởi tạo");
        }

        if (!currentStatus.canTransitionTo(targetStatus)) {
            Set<InvoiceStatus> allowed = currentStatus.allowedNextStates();
            String allowedDesc = allowed.isEmpty() ? "không có (trạng thái kết thúc)" : allowed.toString();
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    String.format(
                            "Không thể chuyển trạng thái hóa đơn từ %s sang %s. Các trạng thái hợp lệ tiếp theo: %s",
                            currentStatus, targetStatus, allowedDesc
                    )
            );
        }

        validateGuards(currentStatus, targetStatus, reason);

        invoice.applyTransition(targetStatus, reason);
        if (targetStatus == InvoiceStatus.RECEIVED || targetStatus == InvoiceStatus.MATCHING) {
            invoice.setStatusReason(null);
        }
        return invoice;
    }

    public Invoice markMatching(Invoice invoice) {
        return transition(invoice, InvoiceStatus.MATCHING, null);
    }

    public Invoice markMatched(Invoice invoice) {
        return transition(invoice, InvoiceStatus.MATCHED, null);
    }

    public Invoice markException(Invoice invoice, String reason) {
        return transition(invoice, InvoiceStatus.EXCEPTION, reason);
    }

    public Invoice markApproved(Invoice invoice, UUID approverId, String reason) {
        if (approverId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thiếu thông tin người phê duyệt (approverId)");
        }
        transition(invoice, InvoiceStatus.APPROVED, reason);
        invoice.setApprovedBy(approverId);
        return invoice;
    }

    public Invoice markPosted(Invoice invoice, UUID posterId) {
        if (posterId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thiếu thông tin người hạch toán (posterId)");
        }
        transition(invoice, InvoiceStatus.POSTED, null);
        invoice.setPostedBy(posterId);
        return invoice;
    }

    public Invoice markRejected(Invoice invoice, String reason) {
        return transition(invoice, InvoiceStatus.REJECTED, reason);
    }

    public Invoice markVoid(Invoice invoice, String reason) {
        return transition(invoice, InvoiceStatus.VOID, reason);
    }

    public Invoice resetToReceived(Invoice invoice) {
        transition(invoice, InvoiceStatus.RECEIVED, null);
        invoice.setApprovedBy(null);
        invoice.setStatusReason(null);
        for (InvoiceLine line : invoice.getLines()) {
            line.resetMatchedQty();
        }
        return invoice;
    }

    private void validateGuards(InvoiceStatus currentStatus, InvoiceStatus targetStatus, String reason) {
        boolean hasReason = reason != null && !reason.isBlank();

        // Bắt buộc nhập lý do khi từ chối hoặc hủy hóa đơn
        if (targetStatus.requiresReason() && !hasReason) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Bắt buộc nhập lý do (reason) khi chuyển hóa đơn sang trạng thái " + targetStatus
            );
        }

        // Bắt buộc giải trình khi phê duyệt ngoại lệ hóa đơn đang bị EXCEPTION
        if (currentStatus == InvoiceStatus.EXCEPTION && targetStatus == InvoiceStatus.APPROVED && !hasReason) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Bắt buộc nhập lý do giải trình khi phê duyệt hóa đơn đang ở trạng thái EXCEPTION"
            );
        }
    }
}