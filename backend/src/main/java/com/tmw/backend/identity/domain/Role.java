package com.tmw.backend.identity.domain;


public enum Role {
    ADMIN,
    MATCHER,
    APPROVER,
    POSTER,
    AUDITOR;
    public String toLowerValue() {
    return this.name().toLowerCase(java.util.Locale.ROOT);
}
}

