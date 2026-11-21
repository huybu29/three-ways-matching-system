package com.tmw.backend.masterdata;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.masterdata.service.PolicySnapshot;
import com.tmw.backend.masterdata.service.ToleranceEvaluation;
import com.tmw.backend.masterdata.service.ToleranceEvaluationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToleranceEvaluationServiceTest {

    private static final UUID SUP_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID SUP_B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

    private static PolicySnapshot policy(String name, UUID supplier, String category, String min, String max,
                                         String qty, String price, String abs, boolean grn, boolean def) {
        return new PolicySnapshot(UUID.randomUUID(), name, supplier, category,
                min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max),
                new BigDecimal(qty), new BigDecimal(price), new BigDecimal(abs), grn, def, Instant.now());
    }

    private static final PolicySnapshot DEFAULT = policy("default", null, null, null, null, "2", "1", "50000", true, true);
    private static final PolicySnapshot SUP = policy("sup", SUP_A, null, null, null, "0", "0.5", "20000", true, false);
    private static final PolicySnapshot SUP_CAT = policy("sup+cat", SUP_A, "IT", null, null, "1", "0.2", "10000", true, false);
    private static final PolicySnapshot CAT = policy("cat", null, "IT", null, null, "3", "1.5", "30000", true, false);
    private static final PolicySnapshot RANGE = policy("range", null, null, "1000000", "5000000", "4", "2", "40000", false, false);

    private ToleranceEvaluationService service(PolicySnapshot... p) {
        return new ToleranceEvaluationService(() -> List.of(p));
    }

    private ToleranceEvaluationService all() {
        return service(DEFAULT, SUP, SUP_CAT, CAT, RANGE);
    }

    private String pick(UUID supplier, String category, String amount) {
        return all().evaluate(supplier, category, new BigDecimal(amount),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true).policyName();
    }

    @Test
    void priorityOrder() {
        assertThat(pick(SUP_A, "IT", "100")).isEqualTo("sup+cat");     // 1. NCC + nhóm hàng
        assertThat(pick(SUP_A, "OTHER", "100")).isEqualTo("sup");      // 2. NCC
        assertThat(pick(SUP_B, "IT", "100")).isEqualTo("cat");         // 3. nhóm hàng
        assertThat(pick(SUP_B, "it", "100")).isEqualTo("cat");         // không phân biệt hoa thường
        assertThat(pick(SUP_B, null, "2000000")).isEqualTo("range");   // 3. khoảng tiền
        assertThat(pick(SUP_B, null, "100")).isEqualTo("default");     // 4. mặc định
        assertThat(pick(SUP_B, null, "5000000.0001")).isEqualTo("default"); // ngoài khoảng tiền
    }

    @Test
    void rangeBoundariesAreInclusive() {
        assertThat(pick(SUP_B, null, "1000000")).isEqualTo("range");
        assertThat(pick(SUP_B, null, "5000000")).isEqualTo("range");
        assertThat(pick(SUP_B, null, "999999.9999")).isEqualTo("default");
    }

    @Test
    void failsWhenNoPolicyMatches() {
        assertThatThrownBy(() -> service(SUP).evaluate(SUP_B, null, BigDecimal.TEN,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true))
                .isInstanceOf(BusinessException.class);
    }

    // Chính sách mặc định: qty 2%, price 1%, amount 50000, require_grn
    // cột: qtyPct, pricePct, amountVar, grnPresent -> qtyOk, priceOk, amountOk, grnOk, within
    @ParameterizedTest(name = "[{index}] qty={0} price={1} amt={2} grn={3}")
    @CsvSource({
            "0,       0,       0,        true,  true,true,true,true,true",
            "2.0000,  1.0000,  50000,    true,  true,true,true,true,true",    // đúng biên -> đạt
            "2.0001,  1.0000,  50000,    true,  false,true,true,true,false",  // vượt biên qty
            "2.0000,  1.0001,  50000,    true,  true,false,true,true,false",  // vượt biên giá
            "2.0000,  1.0000,  50000.0001,true, true,true,false,true,false",  // vượt biên thành tiền
            "2.0000,  1.0000,  -50000,   true,  true,true,true,true,true",    // âm lấy trị tuyệt đối
            "0,       0,       0,        false, true,true,true,false,false",  // thiếu GRN khi bắt buộc
    })
    void defaultPolicyBoundaries(String qty, String price, String amt, boolean grn,
                                 boolean qtyOk, boolean priceOk, boolean amtOk, boolean grnOk, boolean within) {
        ToleranceEvaluation r = service(DEFAULT).evaluate(null, null, new BigDecimal("100"),
                new BigDecimal(qty), new BigDecimal(price), new BigDecimal(amt), grn);
        assertThat(r.qtyWithinTolerance()).isEqualTo(qtyOk);
        assertThat(r.priceWithinTolerance()).isEqualTo(priceOk);
        assertThat(r.amountWithinTolerance()).isEqualTo(amtOk);
        assertThat(r.grnRequirementMet()).isEqualTo(grnOk);
        assertThat(r.withinTolerance()).isEqualTo(within);
        assertThat(r.priorityLevel()).isEqualTo(4);
    }

    @Test
    void missingGrnIsAcceptedWhenPolicyDoesNotRequireIt() {
        ToleranceEvaluation r = service(RANGE).evaluate(SUP_B, null, new BigDecimal("2000000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);
        assertThat(r.grnRequirementMet()).isTrue();
        assertThat(r.withinTolerance()).isTrue();
    }
}
