package com.tmw.backend.auth;

import java.util.Set;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String username,
        UserStatus status,
        Set<Role> roles
) {
    public static UserProfileResponse fromEntity(AppUser user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getStatus(),
                user.getRoles()
        );
    }
}