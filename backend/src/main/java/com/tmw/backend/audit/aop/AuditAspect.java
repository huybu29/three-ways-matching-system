package com.tmw.backend.audit.aop;

import com.tmw.backend.audit.api.AuditContext;
import com.tmw.backend.audit.api.AuditContextHolder;
import com.tmw.backend.audit.api.AuditLogCapturedEvent;
import com.tmw.backend.audit.api.Audited;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@RequiredArgsConstructor
public class AuditAspect {

    private final ApplicationEventPublisher eventPublisher;
    private final AuditSnapshotMapper snapshotMapper;

    private final ExpressionParser spelParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(audited)")
    public Object aroundAuditedMethod(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        System.out.println("AUDIT ASPECT TRIGGERED: " + joinPoint.getSignature().toShortString());

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        String entityType = resolveEntityType(audited);

        MethodBasedEvaluationContext evalContext =
                new MethodBasedEvaluationContext(joinPoint.getTarget(), method, args, nameDiscoverer);

        Map<String, Object> beforeData = captureBeforeState(audited, evalContext);

        Object result;
        Map<String, Object> manualBefore = null;
        try {
            result = joinPoint.proceed();
            manualBefore = AuditContextHolder.consumeManualBeforeSnapshot();
        } finally {
            AuditContextHolder.consumeManualBeforeSnapshot();
        }

        if (manualBefore != null) {
            beforeData = manualBefore;
        }

        evalContext.setVariable("result", result);

        UUID entityId = resolveEntityId(audited, entityType, evalContext, args, result);
        Map<String, Object> afterData = isHardDeleteAction(audited.action())
                ? null
                : snapshotMapper.toSnapshotMap(result);



        AuditContext ctx = AuditContextHolder.getContextOrFallback();
        eventPublisher.publishEvent(new AuditLogCapturedEvent(
                Instant.now(),
                ctx.actorId(),
                ctx.actorRoles(),
                audited.action(),
                entityType,
                entityId,
                beforeData,
                afterData,
                ctx.requestId(),
                ctx.ipAddress()
        ));

        return result;
    }

    private static String resolveEntityType(Audited audited) {
        if (audited.entityType() != null && !audited.entityType().isBlank()) {
            return audited.entityType().trim();
        }
        if (audited.entity() != null && !audited.entity().isBlank()) {
            return audited.entity().trim();
        }
        throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "Annotation @Audited bắt buộc khai báo entityType hoặc entity"
        );
    }

    private Map<String, Object> captureBeforeState(Audited audited, MethodBasedEvaluationContext evalContext) {
        if (audited.before() == null || audited.before().isBlank()) {
            return null;
        }
        Object rawBefore = spelParser.parseExpression(audited.before()).getValue(evalContext);
        return snapshotMapper.toSnapshotMap(rawBefore);
    }

    private UUID resolveEntityId(
            Audited audited,
            String entityType,
            MethodBasedEvaluationContext evalContext,
            Object[] args,
            Object result
    ) {
        if (audited.entityId() != null && !audited.entityId().isBlank()) {
            Object evaluated = spelParser.parseExpression(audited.entityId()).getValue(evalContext);
            UUID parsed = coerceToUuid(evaluated);
            if (parsed != null) {
                return parsed;
            }
        }

        UUID fromResult = extractIdFromObject(result);
        if (fromResult != null) {
            return fromResult;
        }

        for (Object arg : args) {
            if (arg instanceof UUID uuidArg) {
                return uuidArg;
            }
        }

        throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                String.format("Không thể xác định entityId cho @Audited(entityType=%s, action=%s)",
                        entityType, audited.action())
        );
    }

    private static UUID extractIdFromObject(Object target) {
        if (target == null) {
            return null;
        }
        if (target instanceof UUID uuid) {
            return uuid;
        }
        try {
            Method recordAccessor = target.getClass().getMethod("id");
            return coerceToUuid(recordAccessor.invoke(target));
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method getter = target.getClass().getMethod("getId");
            return coerceToUuid(getter.invoke(target));
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static UUID coerceToUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String str && !str.isBlank()) {
            return UUID.fromString(str.trim());
        }
        return null;
    }

    private static boolean isHardDeleteAction(String action) {
        if (action == null) {
            return false;
        }
        String upper = action.trim().toUpperCase(Locale.ROOT);
        return upper.equals("DELETE") || upper.startsWith("DELETE_");
    }
}