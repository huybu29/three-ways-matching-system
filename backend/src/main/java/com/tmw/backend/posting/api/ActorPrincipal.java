package com.tmw.backend.posting.api;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public record ActorPrincipal(
        UUID userId,
        String username,
        Set<String> roles
) {
    public ActorPrincipal {
        if (userId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "userId của người thực hiện không được null");
        }
        roles = roles == null ? Collections.emptySet() : Set.copyOf(roles);
    }

    public boolean hasRole(String role) {
        return role != null && roles.contains(role);
    }
}