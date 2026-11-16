package com.tmw.backend.common.filter;

import com.tmw.backend.auth.AppUser;
import com.tmw.backend.auth.AppUserRepository;
import com.tmw.backend.auth.UserStatus;
import com.tmw.backend.common.security.AuthenticatedUser;
import com.tmw.backend.common.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final AppUserRepository appUserRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                AuthenticatedUser tokenUser = jwtTokenProvider.parseToken(token);
                AppUser dbUser = appUserRepository.findById(tokenUser.id()).orElse(null);

                if (dbUser != null && dbUser.getStatus() == UserStatus.ACTIVE) {
                    AuthenticatedUser principal = new AuthenticatedUser(
                            dbUser.getId(),
                            dbUser.getUsername(),
                            dbUser.getRoles()
                    );

                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    dbUser.getRoles().forEach(role -> {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
                        authorities.add(new SimpleGrantedAuthority(role.toLowerValue()));
                    });

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}