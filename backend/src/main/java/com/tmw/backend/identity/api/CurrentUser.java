package com.tmw.backend.identity.api;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.identity.api.AuthenticatedUser;
import com.tmw.backend.identity.domain.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class CurrentUser {

    public Optional<AuthenticatedUser> getOptional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public AuthenticatedUser get() {
        return getOptional().orElseThrow(() ->
                new BusinessException(ErrorCode.UNAUTHORIZED, "Không tìm thấy thông tin phiên đăng nhập hợp lệ")
        );
    }

    public UUID actorId() {
        return get().id();
    }

    public Optional<UUID> actorIdOrEmpty() {
        return getOptional().map(AuthenticatedUser::id);
    }

    public String username() {
        return get().username();
    }

    public Set<Role> roles() {
        return get().roles();
    }

  
    public Set<String> roleNames() {
        return get().lowerCaseRoles();
    }

    public boolean hasRole(Role role) {
        return getOptional().map(u -> u.hasRole(role)).orElse(false);
    }
}