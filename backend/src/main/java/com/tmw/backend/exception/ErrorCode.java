package com.tmw.backend.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST_PARAMS("TWM-ERR-001", "Dữ liệu đầu vào không hợp lệ", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("TWM-ERR-002", "Không tìm thấy tài nguyên yêu cầu", HttpStatus.NOT_FOUND),
    UNAUTHORIZED_ACCESS("TWM-ERR-003", "Chưa xác thực hoặc token không hợp lệ", HttpStatus.UNAUTHORIZED),
    FORBIDDEN_ACTION("TWM-ERR-004", "Không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    
    PO_NOT_FOUND("TWM-ERR-005", "Không tìm thấy thông tin Đơn đặt hàng (Purchase Order)", HttpStatus.NOT_FOUND),
    GRN_NOT_FOUND("TWM-ERR-006", "Không tìm thấy thông tin Phiếu nhập kho (Goods Receipt Note)", HttpStatus.NOT_FOUND),
    INVOICE_NOT_FOUND("TWM-ERR-007", "Không tìm thấy thông tin Hóa đơn (Invoice)", HttpStatus.NOT_FOUND),
    
    THREE_WAYS_MISMATCH("TWM-ERR-008", "Phát hiện chênh lệch dữ liệu trong quy trình đối soát 3 chiều", HttpStatus.CONFLICT),
    INTERNAL_SERVER_ERROR("TWM-ERR-009", "Lỗi hệ thống nội bộ", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus status;

    ErrorCode(String code, String defaultMessage, HttpStatus status) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }
}