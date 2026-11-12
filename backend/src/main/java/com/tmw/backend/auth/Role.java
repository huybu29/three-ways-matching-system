package com.tmw.backend.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Role {
    ADMIN,
    MATCHER,
    APPROVER,
    POSTER,
    AUDITOR;

    @JsonValue
    public String toLowerValue() {
        return this.name().toLowerCase();
    }

    @JsonCreator
    public static Role fromValue(String value) {
        return Role.valueOf(value.toUpperCase());
    }
}