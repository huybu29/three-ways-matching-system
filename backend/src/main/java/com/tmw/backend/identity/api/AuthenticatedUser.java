package com.tmw.backend.identity.api;
import com.tmw.backend.identity.domain.Role;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record AuthenticatedUser(
        UUID id,
        String username,
        Set<Role> roles
) {
    public Set<String> lowerCaseRoles() {
        if (roles == null) {
            return Set.of();
        }
        return roles.stream()
                .map(r -> r.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean hasRole(Role role) {
        return roles != null && roles.contains(role);
    }
}