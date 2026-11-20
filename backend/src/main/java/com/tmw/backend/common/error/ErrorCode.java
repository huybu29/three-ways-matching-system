package com.tmw.backend.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 401 Unauthorized
    UNAUTHORIZED("Chưa xác thực hoặc token không hợp lệ", HttpStatus.UNAUTHORIZED),

    // 403 Forbidden
    FORBIDDEN("Không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    SOD_VIOLATION("Vi phạm nguyên tắc bất kiêm nhiệm (Segregation of Duties)", HttpStatus.FORBIDDEN),

    // 404 Not Found
    NOT_FOUND("Không tìm thấy tài nguyên yêu cầu", HttpStatus.NOT_FOUND),

    // 409 Conflict
    DUPLICATE_INVOICE("Hóa đơn đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
    ALLOCATION_CAPACITY_EXCEEDED("Số lượng hoặc giá trị phân bổ vượt quá hạn mức PO", HttpStatus.CONFLICT),
    INVALID_STATE_TRANSITION("Trạng thái chứng từ không hợp lệ cho thao tác này", HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_REUSED("Idempotency-Key đã được sử dụng với nội dung yêu cầu khác", HttpStatus.CONFLICT),

    // 412 Precondition Failed
    VERSION_CONFLICT("Phiên bản tài nguyên đã thay đổi. Vui lòng tải lại và thử lại", HttpStatus.PRECONDITION_FAILED),

    // 422 Unprocessable Entity
    VALIDATION_ERROR("Dữ liệu đầu vào không hợp lệ", HttpStatus.UNPROCESSABLE_ENTITY),
    
    // 428 Precondition Required
    PRECONDITION_REQUIRED("Thiếu header If-Match bắt buộc cho thao tác cập nhật", HttpStatus.PRECONDITION_REQUIRED),
    DUPLICATE_RESOURCE("Tài nguyên đã tồn tại", HttpStatus.UNPROCESSABLE_ENTITY),
    RESOURCE_NOT_FOUND("Không tìm thấy tài nguyên", HttpStatus.UNPROCESSABLE_ENTITY),;
    
    private final String defaultTitle;
    private final HttpStatus status;

    public String getCode() {
        return this.name();
    }
}