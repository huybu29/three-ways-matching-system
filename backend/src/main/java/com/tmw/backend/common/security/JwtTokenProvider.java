package com.tmw.backend.common.security;

import com.tmw.backend.auth.AppUser;
import com.tmw.backend.auth.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret:twm-super-secret-jwt-key-must-be-at-least-32-characters-long}") String secret,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(AppUser user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        List<String> roles = user.getRoles().stream()
                .map(Role::toLowerValue)
                .toList();

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public AuthenticatedUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        UUID userId = UUID.fromString(claims.getSubject());
        String username = claims.get("username", String.class);
        @SuppressWarnings("unchecked")
        List<String> roleNames = claims.get("roles", List.class);

        Set<Role> roles = roleNames.stream()
                .map(String::toUpperCase)
                .map(Role::valueOf)
                .collect(Collectors.toSet());

        return new AuthenticatedUser(userId, username, roles);
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }
}