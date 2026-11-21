package com.tmw.backend.masterdata;

import com.tmw.backend.masterdata.service.TaxCrossChecker;
import com.tmw.backend.masterdata.service.TaxCrossChecker.VatLine;
import com.tmw.backend.masterdata.service.TaxFinding;
import com.tmw.backend.masterdata.service.TaxFinding.TaxFindingType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TaxCrossCheckerTest {

    private final TaxCrossChecker checker = new TaxCrossChecker();
    private final UUID line1 = UUID.randomUUID();

    private VatLine line(UUID id, String inv, String po) {
        return new VatLine(id, new BigDecimal(inv), new BigDecimal(po));
    }

    @Test
    void noFindingsWhenEverythingMatches() {
        assertThat(checker.check("0104128565", " 0104128565 ", List.of(line(line1, "0.1000", "0.10")))).isEmpty();
    }

    @Test
    void wrongTaxCodeReturnsExpectedAndActual() {
        List<TaxFinding> f = checker.check("0104128565", "0104128566", List.of());
        assertThat(f).hasSize(1);
        assertThat(f.get(0).type()).isEqualTo(TaxFindingType.TAX_CODE_MISMATCH);
        assertThat(f.get(0).expected()).isEqualTo("0104128565");
        assertThat(f.get(0).actual()).isEqualTo("0104128566");
    }

    @Test
    void missingInvoiceTaxCodeIsMismatch() {
        assertThat(checker.check("0104128565", null, List.of())).hasSize(1);
        assertThat(checker.check("0104128565", "  ", List.of())).hasSize(1);
    }

    @Test
    void wrongVatRateReturnsLineFinding() {
        List<TaxFinding> f = checker.check("1", "1", List.of(line(line1, "0.1000", "0.0800")));
        assertThat(f).hasSize(1);
        assertThat(f.get(0).type()).isEqualTo(TaxFindingType.VAT_RATE_MISMATCH);
        assertThat(f.get(0).invoiceLineId()).isEqualTo(line1);
        assertThat(f.get(0).expected()).isEqualTo("0.0800");
        assertThat(f.get(0).actual()).isEqualTo("0.1000");
    }

    @Test
    void reportsBothKindsTogether() {
        List<TaxFinding> f = checker.check("1", "2", List.of(line(line1, "0.1", "0.08")));
        assertThat(f).extracting(TaxFinding::type)
                .containsExactly(TaxFindingType.TAX_CODE_MISMATCH, TaxFindingType.VAT_RATE_MISMATCH);
    }
}
