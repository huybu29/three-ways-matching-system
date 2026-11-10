package com.tmw.backend.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String customDetail;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.customDetail = null;
    }

    public BusinessException(ErrorCode errorCode, String customDetail) {
        super(customDetail != null ? customDetail : errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.customDetail = customDetail;
    }
}