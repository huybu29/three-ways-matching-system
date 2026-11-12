package com.tmw.backend.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class AuthDataSeeder implements CommandLineRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        seedUser("admin", Role.ADMIN);
        seedUser("matcher", Role.MATCHER);
        seedUser("approver", Role.APPROVER);
        seedUser("poster", Role.POSTER);
        seedUser("auditor", Role.AUDITOR);
    }

    private void seedUser(String username, Role role) {
        if (appUserRepository.findByUsername(username).isEmpty()) {
            AppUser user = AppUser.builder()
                    .username(username)
                    .passwordHash(passwordEncoder.encode("password123"))
                    .status(UserStatus.ACTIVE)
                    .roles(Set.of(role))
                    .build();
            appUserRepository.save(user);
        }
    }
}

