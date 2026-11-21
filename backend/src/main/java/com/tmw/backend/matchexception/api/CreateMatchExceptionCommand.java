package com.tmw.backend.matchexception.api;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record CreateMatchExceptionCommand(
        UUID invoiceId,
        UUID invoiceLineId,
        UUID poLineId,
        UUID matchId,
        ExceptionType exceptionType,
        String message,
        Map<String, Object> details
) {
    public CreateMatchExceptionCommand {
        if (invoiceId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invoiceId không được để trống");
        }
        if (exceptionType == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "exceptionType không được để trống");
        }
        if (message == null || message.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "message không được để trống");
        }
        details = details == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }
}