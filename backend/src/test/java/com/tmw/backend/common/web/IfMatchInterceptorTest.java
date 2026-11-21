package com.tmw.backend.common.web;

import com.tmw.backend.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IfMatchInterceptorTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        IfMatchInterceptor interceptor = new IfMatchInterceptor();
        this.mockMvc = MockMvcBuilders.standaloneSetup(new DummyOptimisticController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(interceptor)
                .build();
    }

    @Test
    void shouldReturn428WhenIfMatchHeaderIsMissing() throws Exception {
        mockMvc.perform(patch("/api/v1/test-items/1"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));
    }

    @Test
    void shouldReturn412WhenIfMatchHeaderIsMalformed() throws Exception {
        mockMvc.perform(patch("/api/v1/test-items/1")
                        .header(HttpHeaders.IF_MATCH, "invalid-etag"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void shouldReturn412WhenVersionDoesNotMatchCurrentResource() throws Exception {
        mockMvc.perform(patch("/api/v1/test-items/1")
                        .header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void shouldReturn200AndNewETagWhenVersionMatches() throws Exception {
        mockMvc.perform(patch("/api/v1/test-items/1")
                        .header(HttpHeaders.IF_MATCH, "\"2\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3\""))
                .andExpect(jsonPath("$.version").value(3));
    }

    @RestController
    static class DummyOptimisticController {

        @RequireIfMatch
        @PatchMapping("/api/v1/test-items/{id}")
        public ResponseEntity<?> updateItem(
                @PathVariable Long id,
                @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch
        ) {
            int currentVersion = 2;
            ETagSupport.verifyMatch(ifMatch, currentVersion);
            int nextVersion = currentVersion + 1;
            return ResponseEntity.ok()
                    .eTag(ETagSupport.format(nextVersion))
                    .body(Map.of("id", id, "version", nextVersion));
        }
    }
}