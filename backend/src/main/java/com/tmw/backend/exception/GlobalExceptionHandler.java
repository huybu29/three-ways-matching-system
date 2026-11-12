package com.tmw.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetailResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        ErrorCode err = ex.getErrorCode();
        ProblemDetailResponse problem = ProblemDetailResponse.builder()
                .type("https://api.threewaysmatching.com/errors/" + err.getCode().toLowerCase())
                .title(err.getDefaultMessage())
                .status(err.getStatus().value())
                .detail(ex.getMessage())
                .instance(request.getRequestURI())
                .code(err.getCode())
                .build();

        return ResponseEntity
                .status(err.getStatus())
                .header(HttpHeaders.CONTENT_TYPE, PROBLEM_JSON.toString())
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetailResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String errorMsg = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .findFirst()
                .orElse("Validation error");

        ProblemDetailResponse problem = ProblemDetailResponse.builder()
                .type("https://api.threewaysmatching.com/errors/twm-err-001")
                .title("Validation Failed")
                .status(ErrorCode.INVALID_REQUEST_PARAMS.getStatus().value())
                .detail(errorMsg)
                .instance(request.getRequestURI())
                .code(ErrorCode.INVALID_REQUEST_PARAMS.getCode())
                .build();

        return ResponseEntity
                .status(ErrorCode.INVALID_REQUEST_PARAMS.getStatus())
                .header(HttpHeaders.CONTENT_TYPE, PROBLEM_JSON.toString())
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetailResponse> handleUncaughtException(Exception ex, HttpServletRequest request) {
        ProblemDetailResponse problem = ProblemDetailResponse.builder()
                .type("https://api.threewaysmatching.com/errors/twm-err-009")
                .title("Internal Server Error")
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus().value())
                .detail(ex.getMessage() != null ? ex.getMessage() : "Unknown error occurred")
                .instance(request.getRequestURI())
                .code(ErrorCode.INTERNAL_SERVER_ERROR.getCode())
                .build();

        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .header(HttpHeaders.CONTENT_TYPE, PROBLEM_JSON.toString())
                .body(problem);
    }
}