package com.tmw.backend.audit.api;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AuditContextHolder {

    private static final ThreadLocal<AuditContext> CONTEXT_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> MANUAL_BEFORE_SNAPSHOT = new ThreadLocal<>();

    private AuditContextHolder() {
    }

    public static void setContext(AuditContext context) {
        CONTEXT_HOLDER.set(context);
    }

    public static AuditContext getContextOrFallback() {
        AuditContext ctx = CONTEXT_HOLDER.get();
        return ctx != null ? ctx : AuditContext.systemFallback();
    }

    /**
     * Cho phép service chụp nhanh trạng thái trước khi mutate entity trong cùng Persistence Context.
     */
    public static void setManualBeforeSnapshot(Map<String, Object> snapshot) {
        MANUAL_BEFORE_SNAPSHOT.set(snapshot == null ? null : new LinkedHashMap<>(snapshot));
    }

    public static Map<String, Object> consumeManualBeforeSnapshot() {
        Map<String, Object> snapshot = MANUAL_BEFORE_SNAPSHOT.get();
        MANUAL_BEFORE_SNAPSHOT.remove();
        return snapshot;
    }

    public static void clear() {
        CONTEXT_HOLDER.remove();
        MANUAL_BEFORE_SNAPSHOT.remove();
    }
}