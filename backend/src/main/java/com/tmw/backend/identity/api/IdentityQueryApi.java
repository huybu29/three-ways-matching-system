package com.tmw.backend.identity.api;

import com.tmw.backend.identity.domain.AppUser;

import java.util.UUID;

public interface IdentityQueryApi {

    AppUser getUserReferenceById(UUID userId);

    AppUser getActiveUserById(UUID userId);

    AppUser getSystemUser();
}