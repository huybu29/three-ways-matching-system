package com.tmw.backend;

import com.tmw.backend.exception.BusinessException;
import com.tmw.backend.exception.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/api/test-error")
    public String testError() {
        // Ném ra ngoại lệ nghiệp vụ định sẵn để test Global Exception Handler
        throw new BusinessException(ErrorCode.PO_NOT_FOUND, "Không tìm thấy mã PO số 999 trong hệ thống");
    }
}