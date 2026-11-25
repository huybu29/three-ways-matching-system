package com.tmw.backend;

import com.tmw.backend.matchexception.api.CreateMatchExceptionCommand;
import com.tmw.backend.matchexception.api.ExceptionFactoryService;
import com.tmw.backend.matchexception.api.ExceptionStatus;
import com.tmw.backend.matchexception.api.ExceptionType;
import com.tmw.backend.matchexception.api.MatchExceptionDto;
import com.tmw.backend.testinfra.PostgresContainerBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ExceptionFactoryServiceImplIntegrationTest extends PostgresContainerBase {

    @Autowired
    private ExceptionFactoryService exceptionFactoryService;

    @Test
    void shouldPersistJsonDetailsAndDeduplicateOpenExceptionsWithNullableLineIds() {
        UUID invoiceId = UUID.randomUUID();
        UUID matchId1 = UUID.randomUUID();
        UUID matchId2 = UUID.randomUUID();

        CreateMatchExceptionCommand first = new CreateMatchExceptionCommand(
                invoiceId, null, null, matchId1,
                ExceptionType.MISSING_PO,
                "Không tìm thấy PO cho hóa đơn",
                Map.of("invoiceNumber", "INV-001", "attempt", 1)
        );

        CreateMatchExceptionCommand duplicateOpen = new CreateMatchExceptionCommand(
                invoiceId, null, null, matchId2,
                ExceptionType.MISSING_PO,
                "Vẫn chưa tìm thấy PO",
                Map.of("invoiceNumber", "INV-001", "attempt", 2, "nested", Map.of("ok", true))
        );

        MatchExceptionDto created = exceptionFactoryService.createOrGetOpen(first);
        MatchExceptionDto deduplicated = exceptionFactoryService.createOrGetOpen(duplicateOpen);

        assertThat(deduplicated.id()).isEqualTo(created.id());
        assertThat(deduplicated.status()).isEqualTo(ExceptionStatus.OPEN);
        assertThat(deduplicated.matchId()).isEqualTo(matchId2);
        assertThat(deduplicated.message()).isEqualTo("Vẫn chưa tìm thấy PO");
        assertThat(deduplicated.details())
                .containsEntry("invoiceNumber", "INV-001")
                .containsEntry("attempt", 2)
                .containsKey("nested");
    }

    @Test
    void shouldAllowCreatingNewOpenExceptionAfterAutoClosingPreviousOne() {
        UUID invoiceId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID poLineId = UUID.randomUUID();

        MatchExceptionDto firstOpen = exceptionFactoryService.createOrGetOpen(
                new CreateMatchExceptionCommand(
                        invoiceId, lineId, poLineId, UUID.randomUUID(),
                        ExceptionType.QTY_VARIANCE, "Lệch SL lần 1", Map.of("diffQty", 2)
                )
        );

        int closedCount = exceptionFactoryService.autoCloseOpenExceptions(
                invoiceId, null, "Hoàn tác đối chiếu"
        );
        assertThat(closedCount).isEqualTo(1);

        MatchExceptionDto secondOpen = exceptionFactoryService.createOrGetOpen(
                new CreateMatchExceptionCommand(
                        invoiceId, lineId, poLineId, UUID.randomUUID(),
                        ExceptionType.QTY_VARIANCE, "Lệch SL lần 2", Map.of("diffQty", 5)
                )
        );

        assertThat(secondOpen.id()).isNotEqualTo(firstOpen.id());
        assertThat(secondOpen.status()).isEqualTo(ExceptionStatus.OPEN);
        assertThat(secondOpen.details()).containsEntry("diffQty", 5);
    }

    @Test
    void shouldDeduplicateBatchCommandsByNaturalKey() {
        UUID invoiceId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();

        CreateMatchExceptionCommand c1 = new CreateMatchExceptionCommand(
                invoiceId, lineId, null, UUID.randomUUID(),
                ExceptionType.TAX_CODE_MISMATCH, "Sai MST v1", Map.of("v", 1)
        );
        CreateMatchExceptionCommand c2 = new CreateMatchExceptionCommand(
                invoiceId, lineId, null, UUID.randomUUID(),
                ExceptionType.TAX_CODE_MISMATCH, "Sai MST v2", Map.of("v", 2)
        );

        List<MatchExceptionDto> result = exceptionFactoryService.createBatchIdempotent(List.of(c1, c2));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).message()).isEqualTo("Sai MST v2");
        assertThat(result.get(0).details()).containsEntry("v", 2);
    }
}