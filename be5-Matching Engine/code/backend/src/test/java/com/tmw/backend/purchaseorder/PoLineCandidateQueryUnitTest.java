package com.tmw.backend.purchaseorder;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.purchaseorder.api.PoLineCandidateQuery;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoLineCandidateQueryUnitTest {

    private static final UUID SUPPLIER = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 11, 27);

    @Test
    void shouldNormalizeCodesNumberAndCurrencyLikePoLineEntity() {
        PoLineCandidateQuery q = new PoLineCandidateQuery(SUPPLIER, DATE, " vnd ",
                new HashSet<>(Arrays.asList(" item-a ", "ITEM-A", "", null, "item-b")),
                new HashSet<>(Arrays.asList(null, UUID.randomUUID())),
                " po-2026-00001 ", null, 100);

        assertThat(q.currency()).isEqualTo("VND");
        assertThat(q.itemCodes()).containsExactlyInAnyOrder("ITEM-A", "ITEM-B");
        assertThat(q.poLineIds()).hasSize(1);
        assertThat(q.poNumber()).isEqualTo("PO-2026-00001");
        assertThat(q.hasNoSelector()).isFalse();
    }

    @Test
    void shouldDetectQueryWithoutAnySelector() {
        PoLineCandidateQuery q = new PoLineCandidateQuery(SUPPLIER, DATE, null, Set.of(), null, "  ", null, 10);
        assertThat(q.hasNoSelector()).isTrue();
    }

    @Test
    void shouldValidateRequiredFieldsAndLimit() {
        assertThatThrownBy(() -> new PoLineCandidateQuery(null, DATE, null, Set.of("A"), null, null, null, 10))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> new PoLineCandidateQuery(SUPPLIER, null, null, Set.of("A"), null, null, null, 10))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> new PoLineCandidateQuery(SUPPLIER, DATE, null, Set.of("A"), null, null, null, 0))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> new PoLineCandidateQuery(SUPPLIER, DATE, null, Set.of("A"), null, null, null,
                PoLineCandidateQuery.MAX_LIMIT + 1))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
