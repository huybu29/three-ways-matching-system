package com.tmw.backend.goodsreceipt;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.goodsreceipt.entity.GoodsReceipt;
import com.tmw.backend.goodsreceipt.entity.GoodsReceiptLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoodsReceiptDomainUnitTest {

    @Test
    void shouldCreateGoodsReceiptAndAddLinesBelongingToSamePurchaseOrder() {
        UUID poId = UUID.randomUUID();
        UUID poLine1Id = UUID.randomUUID();
        UUID poLine2Id = UUID.randomUUID();

        GoodsReceipt grn = new GoodsReceipt(
                "  grn-2026-001 ",
                poId,
                LocalDate.of(2026, 11, 19),
                "Nhập đủ đợt 1",
                UUID.randomUUID()
        );

        GoodsReceiptLine line1 = new GoodsReceiptLine(poLine1Id, new BigDecimal("25.5"), "Đợt 1");
        GoodsReceiptLine line2 = new GoodsReceiptLine(poLine2Id, new BigDecimal("10"), null);

        grn.addLine(line1, poId);
        grn.addLine(line2, poId);

        assertThat(grn.getGrnNumber()).isEqualTo("GRN-2026-001");
        assertThat(grn.getLines()).hasSize(2);
        assertThat(line1.getReceivedQty()).isEqualByComparingTo("25.5000");
        assertThat(line1.getGoodsReceipt()).isSameAs(grn);

        grn.removeLine(line2);
        assertThat(grn.getLines()).hasSize(1);
        assertThat(line2.getGoodsReceipt()).isNull();
    }

    @Test
    void shouldRejectLineBelongingToDifferentPurchaseOrder() {
        UUID grnPoId = UUID.randomUUID();
        UUID anotherPoId = UUID.randomUUID();

        GoodsReceipt grn = new GoodsReceipt("GRN-ERR-PO", grnPoId, LocalDate.now(), null, UUID.randomUUID());
        GoodsReceiptLine foreignLine = new GoodsReceiptLine(UUID.randomUUID(), BigDecimal.TEN, null);

        assertThatThrownBy(() -> grn.addLine(foreignLine, anotherPoId))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("không thuộc đơn đặt hàng");

        assertThatThrownBy(() -> grn.addLine(foreignLine, null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void shouldRejectDuplicatePoLineWithinSameGoodsReceipt() {
        UUID poId = UUID.randomUUID();
        UUID sharedPoLineId = UUID.randomUUID();

        GoodsReceipt grn = new GoodsReceipt("GRN-DUP-LINE", poId, LocalDate.now(), null, UUID.randomUUID());
        GoodsReceiptLine first = new GoodsReceiptLine(sharedPoLineId, new BigDecimal("10"), null);
        GoodsReceiptLine duplicate = new GoodsReceiptLine(sharedPoLineId, new BigDecimal("5"), null);

        grn.addLine(first, poId);

        assertThatThrownBy(() -> grn.addLine(duplicate, poId))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.0001", "-10"})
    void shouldRejectNonPositiveReceivedQty(String invalidQty) {
        assertThatThrownBy(() -> new GoodsReceiptLine(UUID.randomUUID(), new BigDecimal(invalidQty), null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void shouldRejectInvalidHeaderFieldsAndExposeUnmodifiableLines() {
        UUID poId = UUID.randomUUID();
        LocalDate today = LocalDate.now();

        assertThatThrownBy(() -> new GoodsReceipt("  ", poId, today, null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new GoodsReceipt("GRN-1", null, today, null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new GoodsReceipt("GRN-1", poId, null, null, null))
                .isInstanceOf(BusinessException.class);

        GoodsReceipt grn = new GoodsReceipt("GRN-IMMUTABLE", poId, today, null, null);
        GoodsReceiptLine line = new GoodsReceiptLine(UUID.randomUUID(), BigDecimal.ONE, null);

        assertThatThrownBy(() -> grn.getLines().add(line))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}