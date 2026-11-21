package com.tmw.backend.identity.api.dto;

import com.tmw.backend.identity.domain.AppUser;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String username,
        List<String> roles
) {
    public static UserProfileResponse fromEntity(AppUser user) {
        List<String> normalizedRoles = user.getRoles().stream()
                .map(role -> role.name().toLowerCase(Locale.ROOT))
                .sorted()
                .toList();
        return new UserProfileResponse(user.getId(), user.getUsername(), normalizedRoles);
    }
}