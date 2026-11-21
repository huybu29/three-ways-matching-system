package com.tmw.backend.masterdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmw.backend.masterdata.dto.TolerancePolicyRequest;
import com.tmw.backend.masterdata.service.PolicySnapshot;
import com.tmw.backend.masterdata.service.TolerancePolicyCatalog;
import com.tmw.backend.testinfra.PostgresContainerBase;
import com.tmw.backend.testinfra.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TolerancePolicyUpdateIntegrationTest extends PostgresContainerBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TolerancePolicyCatalog catalog;

    private TolerancePolicyRequest request(String name, String category, String pricePct) {
        return new TolerancePolicyRequest(name, null, category, null, null,
                "1.0000", pricePct, "10000.0000", true, false, true);
    }

    private UUID createPolicy(String category) throws Exception {
        String body = mockMvc.perform(post("/api/tolerance-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("P-" + TestFixtures.nextSeq(), category, "0.5000"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private ResultActions doPut(UUID id, String ifMatch, TolerancePolicyRequest req) throws Exception {
        var builder = put("/api/tolerance-policies/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req));
        if (ifMatch != null) {
            builder.header("If-Match", ifMatch);
        }
        return mockMvc.perform(builder);
    }

    private void assertProblem(ResultActions a, int expected) throws Exception {
        a.andExpect(status().is(expected))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.trace_id", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void putWithMatchingIfMatchUpdatesAndBumpsEtag() throws Exception {
        String cat = "CAT-" + TestFixtures.nextSeq();
        UUID id = createPolicy(cat);

        doPut(id, "\"0\"", request("Đã sửa", cat, "0.7500"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.name").value("Đã sửa"))
                .andExpect(jsonPath("$.price_tolerance_pct").value("0.7500"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void putWithoutIfMatchReturns428() throws Exception {
        UUID id = createPolicy("CAT-" + TestFixtures.nextSeq());
        assertProblem(doPut(id, null, request("X", null, "0.5000")), 428);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void putWithStaleIfMatchReturns412() throws Exception {
        String cat = "CAT-" + TestFixtures.nextSeq();
        UUID id = createPolicy(cat);
        doPut(id, "\"0\"", request("Lần 1", cat, "0.6000")).andExpect(status().isOk());
        // version hiện tại đã là 1, gửi lại version cũ 0 -> 412
        assertProblem(doPut(id, "\"0\"", request("Lần 2", cat, "0.6500")), 412);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void putUnknownPolicyReturns404() throws Exception {
        assertProblem(doPut(UUID.randomUUID(), "\"0\"", request("X", null, "0.5000")), 404);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void onlyOneDefaultPolicyAllowedOnUpdate() throws Exception {
        TolerancePolicyRequest asDefault = new TolerancePolicyRequest("Mặc định", null, null, null, null,
                "1.0000", "0.5000", "10000.0000", true, true, true);
        // Chính sách A có thể là mặc định đầu tiên (200) hoặc đã có sẵn mặc định từ seed (422)
        int firstStatus = doPut(createPolicy(null), "\"0\"", asDefault).andReturn().getResponse().getStatus();
        assertThat(firstStatus).isIn(200, 422);
        // Chính sách B chắc chắn bị chặn vì đã tồn tại một mặc định
        assertProblem(doPut(createPolicy(null), "\"0\"", asDefault), 422);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cacheIsEvictedWhenPolicyIsEdited() throws Exception {
        String cat = "CAT-" + TestFixtures.nextSeq();
        UUID id = createPolicy(cat);

        PolicySnapshot before = find(id);
        assertThat(before.priceTolerancePct()).isEqualByComparingTo("0.5");
        assertThat(catalog.activePolicies()).isSameAs(catalog.activePolicies()); // lần 2 lấy từ cache

        doPut(id, "\"0\"", request("Đổi giá", cat, "0.9000")).andExpect(status().isOk());

        assertThat(find(id).priceTolerancePct()).isEqualByComparingTo("0.9");
    }

    private PolicySnapshot find(UUID id) {
        return catalog.activePolicies().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MATCHER", "APPROVER", "POSTER", "AUDITOR"})
    void nonAdminGets403OnPut(String role) throws Exception {
        a(mockMvc.perform(put("/api/tolerance-policies/{id}", UUID.randomUUID())
                .with(SecurityMockMvcRequestPostProcessors.user("u").roles(role))
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request("X", null, "0.5000")))));
    }

    private void a(ResultActions r) throws Exception {
        assertProblem(r, 403);
    }
}
