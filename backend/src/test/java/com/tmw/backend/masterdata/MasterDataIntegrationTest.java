package com.tmw.backend.masterdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmw.backend.masterdata.dto.SupplierRequest;
import com.tmw.backend.masterdata.dto.TolerancePolicyRequest;
import com.tmw.backend.masterdata.repository.SupplierRepository;
import com.tmw.backend.testinfra.ConcurrentTestRunner;
import com.tmw.backend.masterdata.entity.Supplier;
import com.tmw.backend.masterdata.entity.TolerancePolicy;
import com.tmw.backend.masterdata.repository.TolerancePolicyRepository;
import com.tmw.backend.testinfra.PostgresContainerBase;
import com.tmw.backend.testinfra.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MasterDataIntegrationTest extends PostgresContainerBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private TolerancePolicyRepository policyRepository;

    @Test
    @WithMockUser(roles = "AUDITOR")
    void shouldHaveSeedBatch1SuppliersAnd3PolicyTypes() throws Exception {
        // 1. Xác nhận 10 nhà cung cấp seed đợt 1 đã nạp qua Flyway
        assertThat(supplierRepository.count()).isGreaterThanOrEqualTo(10);
        assertThat(supplierRepository.findByCodeIgnoreCase("SUP-FPTIS")).isPresent();
        assertThat(supplierRepository.findByTaxCode("0305482862-001")).isPresent();

        // 2. Xác nhận 3 loại chính sách (mặc định, theo nhà cung cấp, theo nhóm hàng)
        List<TolerancePolicy> policies = policyRepository.findAll();
        assertThat(policies).anyMatch(TolerancePolicy::isDefault);
        assertThat(policies).anyMatch(p -> p.getSupplier() != null);
        assertThat(policies).anyMatch(p -> p.getItemCategory() != null);

        mockMvc.perform(get("/api/suppliers").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.page_meta.next_cursor", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldCreateAndFetchSupplierThenRejectDuplicateCodeOrTaxCodeWith409() throws Exception {
        String code = "SUP-INT-" + TestFixtures.nextSeq();
        String taxCode = TestFixtures.nextTaxCode();

        SupplierRequest createReq = new SupplierRequest(
                code,
                "Công ty TNHH Tích Hợp",
                taxCode,
                "Quận 1, TP. Hồ Chí Minh"
        );

        String responseBody = mockMvc.perform(post("/api/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.tax_code").value(taxCode))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID createdId = UUID.fromString(objectMapper.readTree(responseBody).get("id").asText());

        mockMvc.perform(get("/api/suppliers/{supplierId}", createdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdId.toString()))
                .andExpect(jsonPath("$.tax_code").value(taxCode));

        // Trùng tax_code -> 409 application/problem+json kèm trace_id
        SupplierRequest dupTaxReq = new SupplierRequest(
                "SUP-OTHER-" + TestFixtures.nextSeq(),
                "Công ty Trùng MST",
                taxCode,
                null
        );
        assertProblemResponse(
                mockMvc.perform(post("/api/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupTaxReq))),
                409
        );

        // Trùng code (khác chữ hoa/thường) -> 409 application/problem+json kèm trace_id
        SupplierRequest dupCodeReq = new SupplierRequest(
                code.toLowerCase(),
                "Công ty Trùng Mã",
                TestFixtures.nextTaxCode(),
                null
        );
        assertProblemResponse(
                mockMvc.perform(post("/api/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupCodeReq))),
                409
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldHandleConcurrentDuplicateSupplierRequestsWithout500Error() throws Exception {
        String sharedTaxCode = TestFixtures.nextTaxCode();

        var result = ConcurrentTestRunner.runSameTask(5, () -> {
            SupplierRequest req = new SupplierRequest(
                    "SUP-CONC-" + TestFixtures.nextSeq(),
                    "Công ty Đồng Thời",
                    sharedTaxCode,
                    null
            );
            return mockMvc.perform(post("/api/suppliers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        });

        assertThat(result.failureCount()).isZero();
        List<Integer> statuses = result.successValues();
        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(4);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldCreatePoliciesAndEnforceSingleDefaultPolicyInPostgres() throws Exception {
        Supplier supplier = supplierRepository.saveAndFlush(TestFixtures.supplier().build());

        // Tạo chính sách theo nhà cung cấp -> 201
        TolerancePolicyRequest supplierPolicyReq = new TolerancePolicyRequest(
                "Policy riêng NCC " + supplier.getCode(),
                supplier.getId(),
                null,
                "0.0000",
                "100000000.0000",
                "1.0000",
                "0.5000",
                "10000.0000",
                true,
                false,
                true
        );

        mockMvc.perform(post("/api/tolerance-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supplierPolicyReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.supplier_id").value(supplier.getId().toString()));

        // Tạo chính sách mặc định thứ 2 (đã có 1 bản ghi trong seed đợt 1) -> bị chặn 409
        TolerancePolicyRequest secondDefaultReq = new TolerancePolicyRequest(
                "Policy mặc định trùng",
                null,
                null,
                null,
                null,
                "2.0000",
                "1.0000",
                "50000.0000",
                true,
                true,
                true
        );

        assertProblemResponse(
                mockMvc.perform(post("/api/tolerance-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondDefaultReq))),
                409
        );

        // Kiểm chứng trực tiếp partial unique index ở tầng PostgreSQL
        TolerancePolicy duplicateDefaultEntity = TestFixtures.tolerancePolicy()
                .asDefault(true)
                .build();
        assertThatThrownBy(() -> policyRepository.saveAndFlush(duplicateDefaultEntity))
                .isInstanceOf(DataIntegrityViolationException.class);

        mockMvc.perform(get("/api/tolerance-policies").param("supplier_id", supplier.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(1))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MATCHER", "APPROVER", "POSTER", "AUDITOR"})
    void shouldReturn403ForNonAdminRolesOnWriteEndpoints(String role) throws Exception {
        SupplierRequest supplierReq = new SupplierRequest(
                "SUP-403-" + TestFixtures.nextSeq(),
                "Công ty Không Quyền",
                TestFixtures.nextTaxCode(),
                null
        );

        assertProblemResponse(
                mockMvc.perform(post("/api/suppliers")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("user").roles(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supplierReq))),
                403
        );

        TolerancePolicyRequest policyReq = new TolerancePolicyRequest(
                "Policy Không Quyền",
                null,
                "OFFICE_SUPPLIES",
                null,
                null,
                "2.0000",
                "1.0000",
                "10000.0000",
                true,
                false,
                true
        );

        assertProblemResponse(
                mockMvc.perform(post("/api/tolerance-policies")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("user").roles(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(policyReq))),
                403
        );
    }

    private void assertProblemResponse(ResultActions actions, int expectedStatus) throws Exception {
        actions.andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.trace_id", notNullValue()));
    }
}