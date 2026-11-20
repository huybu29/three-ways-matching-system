package com.tmw.backend.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginResponse(
        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("refresh_token")
        String refreshToken,

        @JsonProperty("expires_in")
        long expiresIn,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("user")
        UserProfileResponse user
) {
    public LoginResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            UserProfileResponse user
    ) {
        this(accessToken, refreshToken, expiresIn, "Bearer", user);
    }
}