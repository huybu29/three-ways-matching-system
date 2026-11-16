package com.tmw.backend.common.security;

import com.tmw.backend.auth.Role;
import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        String username,
        Set<Role> roles
) {}