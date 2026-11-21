package com.tmw.backend.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmw.backend.identity.domain.Role;
import com.tmw.backend.identity.domain.AppUser;
import com.tmw.backend.identity.domain.AppUserRepository;
import com.tmw.backend.identity.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AuthAndSecurityIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("twm_test")
            .withUsername("twm")
            .withPassword("twm");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.jwt.secret", () -> "integration-test-jwt-secret-key-minimum-32-bytes");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldSeedAllRequiredUsersIncludingDualRoleAndSystemUser() {
        assertThat(appUserRepository.findByUsername("admin")).isPresent();
        assertThat(appUserRepository.findByUsername("matcher")).isPresent();
        assertThat(appUserRepository.findByUsername("approver")).isPresent();
        assertThat(appUserRepository.findByUsername("poster")).isPresent();
        assertThat(appUserRepository.findByUsername("auditor")).isPresent();
        assertThat(appUserRepository.findByUsername("system")).isPresent();

        AppUser dualRoleUser = appUserRepository.findByUsername("matcher_approver").orElseThrow();
        assertThat(dualRoleUser.getRoles()).containsExactlyInAnyOrder(Role.MATCHER, Role.APPROVER);
    }

    @Test
    void shouldLoginAndFetchCurrentUserProfileSuccessfully() throws Exception {
        String loginPayload = "{\"username\":\"matcher_approver\",\"password\":\"Password@123\"}";

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.expires_in").isNumber())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginJson.get("access_token").asText();

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.username").value("matcher_approver"))
                .andExpect(jsonPath("$.roles[0]").value("approver"))
                .andExpect(jsonPath("$.roles[1]").value("matcher"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void shouldReturnProblemJsonWithTraceIdWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header("X-Trace-Id", "trace-unauth-check-001"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Trace-Id", "trace-unauth-check-001"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.trace_id").value("trace-unauth-check-001"));

        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void shouldBlockSystemUserFromInteractiveLogin() throws Exception {
        String loginPayload = "{\"username\":\"system\",\"password\":\"Password@123\"}";

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.trace_id").isNotEmpty());
    }

    @Test
    void shouldPreventUserEnumerationOnDisabledAccount() throws Exception {
        appUserRepository.save(AppUser.builder()
                .username("locked_user")
                .passwordHash(passwordEncoder.encode("Secret@123"))
                .status(UserStatus.DISABLED)
                .roles(Set.of(Role.MATCHER))
                .build());

        // 1. Sai mật khẩu -> trả 401 (không để lộ tài khoản tồn tại hay bị khóa)
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"locked_user\",\"password\":\"WrongPass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // 2. Đúng mật khẩu -> mới báo 403 FORBIDDEN do tài khoản bị khóa
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"locked_user\",\"password\":\"Secret@123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldReturn422ValidationErrorInProblemJsonFormat() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.trace_id").isNotEmpty())
                .andExpect(jsonPath("$.errors").isArray());
    }
}