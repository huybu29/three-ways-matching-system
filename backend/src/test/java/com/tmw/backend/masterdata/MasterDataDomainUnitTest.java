package com.tmw.backend.masterdata;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.testinfra.TestFixtures;
import com.tmw.backend.masterdata.entity.Supplier;
import com.tmw.backend.masterdata.entity.TolerancePolicy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MasterDataDomainUnitTest {

    @Test
    void shouldCreateSupplierWithValid10And13DigitTaxCodes() {
        Supplier headOffice = new Supplier("  sup-01 ", " Công ty A ", "0104128565", " Hà Nội ");
        Supplier branch = new Supplier("sup-02", "Chi nhánh B", "0305482862-001", null);

        assertThat(headOffice.getCode()).isEqualTo("SUP-01");
        assertThat(headOffice.getName()).isEqualTo("Công ty A");
        assertThat(headOffice.getTaxCode()).isEqualTo("0104128565");
        assertThat(headOffice.getAddress()).isEqualTo("Hà Nội");

        assertThat(branch.getCode()).isEqualTo("SUP-02");
        assertThat(branch.getTaxCode()).isEqualTo("0305482862-001");
        assertThat(branch.getAddress()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "123456789", "12345678901", "0104128565-01", "0104128565-0001", "010412856A"})
    void shouldRejectInvalidTaxCodeFormat(String invalidTaxCode) {
        assertThatThrownBy(() -> new Supplier("SUP-ERR", "Công ty Lỗi", invalidTaxCode, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectPolicyWhenMinAmountExceedsMaxAmount() {
        assertThatThrownBy(() -> TestFixtures.tolerancePolicy()
                .amountRange("50000000", "10000000")
                .build())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("min_amount");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-10", "100.01", "150"})
    void shouldRejectPolicyWhenPercentageOutOfRange0To100(String invalidPct) {
        assertThatThrownBy(() -> TestFixtures.tolerancePolicy()
                .qtyTolerancePct(invalidPct)
                .build())
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> TestFixtures.tolerancePolicy()
                .priceTolerancePct(invalidPct)
                .build())
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectDefaultPolicyWhenBoundToSupplierOrCategory() {
        Supplier supplier = TestFixtures.supplier().build();

        assertThatThrownBy(() -> TestFixtures.tolerancePolicy()
                .asDefault(true)
                .supplier(supplier)
                .build())
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> TestFixtures.tolerancePolicy()
                .asDefault(true)
                .itemCategory("IT_HARDWARE")
                .build())
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldAllowBoundaryPercentages0And100AndEqualMinMaxAmount() {
        TolerancePolicy policy = TestFixtures.tolerancePolicy()
                .amountRange("1000000", "1000000")
                .qtyTolerancePct("0")
                .priceTolerancePct("100")
                .amountToleranceAbs("0")
                .build();

        assertThat(policy.getMinAmount()).isEqualByComparingTo(new BigDecimal("1000000.0000"));
        assertThat(policy.getMaxAmount()).isEqualByComparingTo(new BigDecimal("1000000.0000"));
        assertThat(policy.getQtyTolerancePct()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(policy.getPriceTolerancePct()).isEqualByComparingTo(new BigDecimal("100.0000"));
    }
}