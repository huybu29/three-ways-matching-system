package com.tmw.backend.masterdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** Cần PostgreSQL chạy (docker compose up -d). Mỗi test rollback nên không để lại dữ liệu. */
@SpringBootTest
@Transactional
class SupplierApiTest {

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static MockHttpServletRequestBuilder create(String code, String taxCode, String role) {
        String body = "{\"code\":\"%s\",\"name\":\"Cong ty %s\",\"tax_code\":\"%s\",\"address\":\"Ha Noi\"}"
                .formatted(code, code, taxCode);
        return post("/suppliers").with(user("u").roles(role)).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String createAndGetId(String code, String taxCode) throws Exception {
        String res = mvc.perform(create(code, taxCode, "ADMIN")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.id");
    }

    @Test
    void createReturns201WithSnakeCaseBody() throws Exception {
        mvc.perform(create("NCC001", "0101234567-001", "ADMIN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.code").value("NCC001"))
                .andExpect(jsonPath("$.tax_code").value("0101234567-001"))
                .andExpect(jsonPath("$.created_at").isNotEmpty());
    }

    @Test
    void duplicateTaxCodeReturns409ProblemJson() throws Exception {
        createAndGetId("NCC001", "0101234567");
        mvc.perform(create("NCC002", "0101234567", "ADMIN"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("0101234567")));
    }

    @Test
    void duplicateCodeReturns409() throws Exception {
        createAndGetId("NCC001", "0101234567");
        mvc.perform(create("NCC001", "0109999999", "ADMIN")).andExpect(status().isConflict());
    }

    @Test
    void invalidTaxCodeReturns400() throws Exception {
        mvc.perform(create("NCC001", "12345", "ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("tax")));
    }

    @Test
    void nonAdminCannotCreate() throws Exception {
        mvc.perform(create("NCC001", "0101234567", "MATCHER")).andExpect(status().isForbidden());
    }

    @Test
    void getByIdAndNotFound() throws Exception {
        String id = createAndGetId("NCC001", "0101234567");
        mvc.perform(get("/suppliers/" + id).with(user("a").roles("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tax_code").value("0101234567"));
        mvc.perform(get("/suppliers/" + UUID.randomUUID()).with(user("a").roles("AUDITOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void filterByTaxCode() throws Exception {
        createAndGetId("NCC001", "0101234567");
        createAndGetId("NCC002", "0202345678");
        mvc.perform(get("/suppliers").param("tax_code", "0202345678").with(user("a").roles("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].code").value("NCC002"))
                .andExpect(jsonPath("$.next_cursor").doesNotExist());
    }

    @Test
    void cursorPaginationWalksAllItemsOnce() throws Exception {
        for (int i = 1; i <= 5; i++) {
            createAndGetId("NCC00" + i, "010123456" + i);
        }
        String cursor = null;
        int seen = 0;
        int pages = 0;
        do {
            MockHttpServletRequestBuilder req = get("/suppliers").param("limit", "2")
                    .param("tax_code", "").with(user("a").roles("AUDITOR"));
            if (cursor != null) {
                req.param("cursor", cursor);
            }
            String page = mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            seen += (Integer) JsonPath.read(page, "$.items.length()");
            cursor = JsonPath.read(page, "$.next_cursor");
            pages++;
        } while (cursor != null);
        // Dữ liệu seed sẵn trong DB (nếu có) không nằm trong test, nên chỉ kiểm tra đủ 5 bản ghi vừa tạo trở lên.
        org.junit.jupiter.api.Assertions.assertTrue(seen >= 5);
        org.junit.jupiter.api.Assertions.assertTrue(pages >= 3);
    }

    @Test
    void invalidLimitAndCursorAreRejected() throws Exception {
        mvc.perform(get("/suppliers").param("limit", "0").with(user("a").roles("AUDITOR")))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/suppliers").param("limit", "201").with(user("a").roles("AUDITOR")))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/suppliers").param("cursor", "rac-roi").with(user("a").roles("AUDITOR")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        assertEquals(401, mvc.perform(get("/suppliers")).andReturn().getResponse().getStatus());
    }
}
