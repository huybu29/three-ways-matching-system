package com.tmw.backend.identity.seed;

import com.tmw.backend.identity.domain.AppUser;
import com.tmw.backend.identity.domain.AppUserRepository;
import com.tmw.backend.identity.domain.Role;
import com.tmw.backend.identity.domain.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityDataSeeder implements ApplicationRunner {

    public static final String SYSTEM_USERNAME = "system";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        
        seedSystemUser();

        
        if (environment.acceptsProfiles(Profiles.of("local", "dev", "test", "demo"))) {
            String defaultEncodedPassword = passwordEncoder.encode("Password@123");

            seedDemoUser("admin", defaultEncodedPassword, Set.of(Role.ADMIN));
            seedDemoUser("matcher", defaultEncodedPassword, Set.of(Role.MATCHER));
            seedDemoUser("approver", defaultEncodedPassword, Set.of(Role.APPROVER));
            seedDemoUser("poster", defaultEncodedPassword, Set.of(Role.POSTER));
            seedDemoUser("auditor", defaultEncodedPassword, Set.of(Role.AUDITOR));

            // 1 người dùng sở hữu 2 vai trò để test bất kiêm nhiệm (SoD) ở Giai đoạn 4 & 5
            seedDemoUser("matcher_approver", defaultEncodedPassword, Set.of(Role.MATCHER, Role.APPROVER));
        }
    }

    private void seedSystemUser() {
        if (appUserRepository.findByUsername(SYSTEM_USERNAME).isEmpty()) {
            AppUser systemUser = AppUser.builder()
                    .username(SYSTEM_USERNAME)
                   
                    .passwordHash(passwordEncoder.encode("SYSTEM_LOCKED_" + UUID.randomUUID()))
                    .status(UserStatus.ACTIVE)
                    .roles(new HashSet<>(Set.of(Role.ADMIN)))
                    .build();
            appUserRepository.save(systemUser);
            log.info("Đã khởi tạo tài khoản hệ thống '{}'", SYSTEM_USERNAME);
        }
    }

    private void seedDemoUser(String username, String encodedPassword, Set<Role> roles) {
        if (appUserRepository.findByUsername(username).isEmpty()) {
            AppUser user = AppUser.builder()
                    .username(username)
                    .passwordHash(encodedPassword)
                    .status(UserStatus.ACTIVE)
                    .roles(new HashSet<>(roles))
                    .build();
            appUserRepository.save(user);
        }
    }
}