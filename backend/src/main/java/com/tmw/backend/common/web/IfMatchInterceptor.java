package com.tmw.backend.common.web;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class IfMatchInterceptor implements HandlerInterceptor {

    public static final String REQUEST_IF_MATCH_VERSION_ATTR = "TMW_IF_MATCH_VERSION";

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        boolean required = AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), RequireIfMatch.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), RequireIfMatch.class);

        if (!required) {
            return true;
        }

        String ifMatch = request.getHeader(HttpHeaders.IF_MATCH);
        if (!StringUtils.hasText(ifMatch)) {
            throw new BusinessException(
                    ErrorCode.PRECONDITION_REQUIRED,
                    "Thiếu header bắt buộc If-Match cho thao tác cập nhật."
            );
        }

        if (!"*".equals(ifMatch.trim())) {
            int version = ETagSupport.parseVersion(ifMatch);
            request.setAttribute(REQUEST_IF_MATCH_VERSION_ATTR, version);
        }

        return true;
    }
}
