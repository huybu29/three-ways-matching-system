package com.tmw.backend.common.error;

import lombok.Getter;

import java.util.List;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ProblemFieldError> errors;
    private final Object capacity;

    public BusinessException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, null, null);
    }

    public BusinessException(ErrorCode errorCode, String detail, List<ProblemFieldError> errors) {
        this(errorCode, detail, errors, null);
    }

    private BusinessException(
            ErrorCode errorCode,
            String detail,
            List<ProblemFieldError> errors,
            Object capacity
    ) {
        super(detail);
        this.errorCode = errorCode;
        this.errors = (errors == null || errors.isEmpty()) ? null : List.copyOf(errors);
        this.capacity = capacity;
    }

    public static BusinessException capacityExceeded(String detail, Object capacitySnapshot) {
        return new BusinessException(
                ErrorCode.ALLOCATION_CAPACITY_EXCEEDED,
                detail,
                null,
                capacitySnapshot
        );
    }
}