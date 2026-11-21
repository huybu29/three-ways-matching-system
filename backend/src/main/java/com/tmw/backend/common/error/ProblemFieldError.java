package com.tmw.backend.common.error;

public record ProblemFieldError(
        String field,
        String message
) {
}