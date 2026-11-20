package com.tmw.backend.common.error;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;
    private static final String ERROR_TYPE_BASE = "https://api.example.com/errors/";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetailResponse> handleBusinessException(
            BusinessException ex, HttpServletRequest request
    ) {
        ErrorCode err = ex.getErrorCode();
        ProblemDetailResponse problem = ProblemDetailResponse.builder()
                .type(buildTypeUri(err.getCode()))
                .title(err.getDefaultTitle())
                .status(err.getStatus().value())
                .code(err.getCode())
                .detail(ex.getMessage())
                .instance(request.getRequestURI())
                .traceId(resolveTraceId(request))
                .errors(ex.getErrors())
                .capacity(err == ErrorCode.ALLOCATION_CAPACITY_EXCEEDED ? ex.getCapacity() : null)
                .build();

        return buildResponse(err.getStatus(), problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetailResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request
    ) {
        List<ProblemFieldError> fieldErrors = new ArrayList<>();

        ex.getBindingResult().getFieldErrors().forEach(err ->
                fieldErrors.add(new ProblemFieldError(
                        err.getField(),
                        err.getDefaultMessage() != null ? err.getDefaultMessage() : "Giá trị không hợp lệ"
                ))
        );

        ex.getBindingResult().getGlobalErrors().forEach(err ->
                fieldErrors.add(new ProblemFieldError(
                        err.getObjectName(),
                        err.getDefaultMessage() != null ? err.getDefaultMessage() : "Dữ liệu không hợp lệ"
                ))
        );

        return buildValidationResponse(
                "Dữ liệu đầu vào không hợp lệ. Vui lòng kiểm tra danh sách lỗi chi tiết.",
                fieldErrors,
                request
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetailResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request
    ) {
        List<ProblemFieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> new ProblemFieldError(
                        v.getPropertyPath().toString(),
                        v.getMessage()
                ))
                .toList();

        return buildValidationResponse("Tham số yêu cầu không hợp lệ.", fieldErrors, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetailResponse> handleMalformedRequest(
            Exception ex, HttpServletRequest request
    ) {
        return buildValidationResponse("Định dạng dữ liệu hoặc kiểu tham số không hợp lệ.", null, request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetailResponse> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request
    ) {
        if ("If-Match".equalsIgnoreCase(ex.getHeaderName())) {
            ErrorCode err = ErrorCode.PRECONDITION_REQUIRED;
            ProblemDetailResponse problem = ProblemDetailResponse.builder()
                    .type(buildTypeUri(err.getCode()))
                    .title(err.getDefaultTitle())
                    .status(err.getStatus().value())
                    .code(err.getCode())
                    .detail("Thiếu header bắt buộc If-Match.")
                    .instance(request.getRequestURI())
                    .traceId(resolveTraceId(request))
                    .build();
            return buildResponse(err.getStatus(), problem);
        }
        return buildValidationResponse("Thiếu header bắt buộc: " + ex.getHeaderName(), null, request);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ProblemDetailResponse> handleOptimisticLock(
            Exception ex, HttpServletRequest request
    ) {
        ErrorCode err = ErrorCode.VERSION_CONFLICT;
        ProblemDetailResponse problem = ProblemDetailResponse.builder()
                .type(buildTypeUri(err.getCode()))
                .title(err.getDefaultTitle())
                .status(err.getStatus().value())
                .code(err.getCode())
                .detail("Phiên bản tài nguyên đã thay đổi. Vui lòng tải lại dữ liệu mới nhất và thử lại.")
                .instance(request.getRequestURI())
                .traceId(resolveTraceId(request))
                .build();

        return buildResponse(err.getStatus(), problem);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetailResponse> handleNotFound(
            NoResourceFoundException ex, HttpServletRequest request
    ) {
        ErrorCode err = ErrorCode.NOT_FOUND;
        ProblemDetailResponse problem = ProblemDetailResponse.builder()
                .type(buildTypeUri(err.getCode()))
                .title(err.getDefaultTitle())
                .status(err.getStatus().value())
                .code(err.getCode())
                .detail("Không tìm thấy tài nguyên yêu cầu.")
                .instance(request.getRequestURI())
                .traceId(resolveTraceId(request))
                .build();

        return buildResponse(err.getStatus(), problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetailResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request
    ) {
        ErrorCode err = ErrorCode.UNAUTHORIZED;
        ProblemDetailResponse problem = ProblemDetailResponse.builder()
                .type(buildTypeUri(err.getCode()))
                .title(err.getDefaultTitle())
                .status(err.getStatus().value())
                .code(err.getCode())
                .detail(ex.getMessage())
                .instance(request.getRequestURI())
                .traceId(resolveTraceId(request))
                .build();

        return buildResponse(err.getStatus(), problem);
    }

    @ExceptionHandler({AccessDeniedException.class, DisabledException.class})
    public ResponseEntity<ProblemDetailResponse> handleAccessDeniedException(
            RuntimeException ex, HttpServletRequest request
    ) {
        ErrorCode err = ErrorCode.FORBIDDEN;
        ProblemDetailResponse problem = ProblemDetailResponse.builder()
                .type(buildTypeUri(err.getCode()))
                .title(err.getDefaultTitle())
                .status(err.getStatus().value())
                .code(err.getCode())
                .detail(ex.getMessage())
                .instance(request.getRequestURI())
                .traceId(resolveTraceId(request))
                .build();

        return buildResponse(err.getStatus(), problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetailResponse> handleUncaughtException(
            Exception ex, HttpServletRequest request
    ) {
        String traceId = resolveTraceId(request);
        log.error("Unhandled exception [traceId={}, uri={}]", traceId, request.getRequestURI(), ex);

        ProblemDetailResponse problem = ProblemDetailResponse.builder()
                .type(ERROR_TYPE_BASE + "internal-server-error")
                .title("Internal Server Error")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .code("INTERNAL_SERVER_ERROR")
                .detail("Đã xảy ra lỗi hệ thống. Vui lòng cung cấp trace_id cho bộ phận kỹ thuật.")
                .instance(request.getRequestURI())
                .traceId(traceId)
                .build();

        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, problem);
    }

    private ResponseEntity<ProblemDetailResponse> buildValidationResponse(
            String detail,
            List<ProblemFieldError> fieldErrors,
            HttpServletRequest request
    ) {
        ErrorCode err = ErrorCode.VALIDATION_ERROR;
        ProblemDetailResponse problem = ProblemDetailResponse.builder()
                .type(buildTypeUri(err.getCode()))
                .title(err.getDefaultTitle())
                .status(err.getStatus().value())
                .code(err.getCode())
                .detail(detail)
                .instance(request.getRequestURI())
                .traceId(resolveTraceId(request))
                .errors(fieldErrors)
                .build();

        return buildResponse(err.getStatus(), problem);
    }

    private ResponseEntity<ProblemDetailResponse> buildResponse(HttpStatus status, ProblemDetailResponse body) {
        return ResponseEntity
                .status(status)
                .contentType(PROBLEM_JSON)
                .body(body);
    }

    private String buildTypeUri(String code) {
        return ERROR_TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        String headerId = request.getHeader("X-Trace-Id");
        return (headerId != null && !headerId.isBlank()) ? headerId : UUID.randomUUID().toString();
    }
}