package com.tmw.backend.identity.security;

import com.tmw.backend.identity.api.AuthenticatedUser;
import com.tmw.backend.identity.domain.AppUser;
import com.tmw.backend.identity.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLES = "roles";
    private static final String SYSTEM_USERNAME = "system";

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms:3600000}") long expirationMs
    ) {
        if (!StringUtils.hasText(secret) || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("app.jwt.secret phải được cấu hình và dài tối thiểu 32 bytes");
        }
        if (expirationMs <= 0) {
            throw new IllegalArgumentException("app.jwt.expiration-ms phải lớn hơn 0");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(AppUser user) {
        Objects.requireNonNull(user, "user không được null");
        Objects.requireNonNull(user.getId(), "user.id không được null khi tạo token");

        if (SYSTEM_USERNAME.equalsIgnoreCase(user.getUsername())) {
            throw new IllegalArgumentException("Không được cấp JWT đăng nhập cho tài khoản system");
        }

        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        List<String> roles = user.getRoles().stream()
                .map(role -> role.name().toLowerCase(Locale.ROOT))
                .toList();

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_ROLES, roles)
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

        String subject = claims.getSubject();
        String username = claims.get(CLAIM_USERNAME, String.class);
        List<?> rawRoles = claims.get(CLAIM_ROLES, List.class);

        if (!StringUtils.hasText(subject) || !StringUtils.hasText(username) || rawRoles == null || rawRoles.isEmpty()) {
            throw new JwtException("JWT thiếu thông tin định danh hoặc vai trò bắt buộc");
        }

        try {
            UUID userId = UUID.fromString(subject);
            Set<Role> roles = rawRoles.stream()
                    .map(Object::toString)
                    .map(r -> Role.valueOf(r.toUpperCase(Locale.ROOT)))
                    .collect(Collectors.toUnmodifiableSet());

            return new AuthenticatedUser(userId, username, roles);
        } catch (IllegalArgumentException ex) {
            throw new JwtException("Dữ liệu subject hoặc role trong JWT không hợp lệ", ex);
        }
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }
}