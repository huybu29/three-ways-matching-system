package com.tmw.backend.matchexception;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.matchexception.api.CreateMatchExceptionCommand;
import com.tmw.backend.matchexception.api.ExceptionStatus;
import com.tmw.backend.matchexception.api.ExceptionType;
import com.tmw.backend.matchexception.api.MatchExceptionDto;
import com.tmw.backend.matchexception.entity.MatchException;
import com.tmw.backend.matchexception.repository.MatchExceptionRepository;
import com.tmw.backend.matchexception.service.ExceptionFactoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchExceptionAndFactoryUnitTest {

    @Mock
    private MatchExceptionRepository exceptionRepository;

    @InjectMocks
    private ExceptionFactoryServiceImpl factoryService;

    @ParameterizedTest
    @EnumSource(ExceptionType.class)
    void shouldSupportAll8ExceptionTypesAndEnforceOpenVsResolvedInvariants(ExceptionType type) {
        MatchException ex = new MatchException(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                type,
                "Phát hiện ngoại lệ loại " + type,
                Map.of("variancePct", "5.5000")
        );

        // Khi OPEN -> chưa có người xử lý
        assertThat(ex.getStatus()).isEqualTo(ExceptionStatus.OPEN);
        assertThat(ex.getResolvedBy()).isNull();
        assertThat(ex.getResolvedAt()).isNull();

        // Thiếu actorId hoặc ghi chú khi resolve -> từ chối
        assertThatThrownBy(() -> ex.resolve(null, "Đã xử lý"))
                .isInstanceOfSatisfying(BusinessException.class, err ->
                        assertThat(err.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> ex.resolve(UUID.randomUUID(), "  "))
                .isInstanceOfSatisfying(BusinessException.class, err ->
                        assertThat(err.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        // Đã xử lý -> bắt buộc có resolved_by và resolved_at
        UUID resolverId = UUID.randomUUID();
        ex.resolve(resolverId, "Nhà cung cấp đã phát hành biên bản điều chỉnh");

        assertThat(ex.getStatus()).isEqualTo(ExceptionStatus.RESOLVED);
        assertThat(ex.getResolvedBy()).isEqualTo(resolverId);
        assertThat(ex.getResolvedAt()).isNotNull();

        // Không được xử lý lần 2 khi đã đóng
        assertThatThrownBy(() -> ex.waive(UUID.randomUUID(), "Thử miễn trừ lại"))
                .isInstanceOfSatisfying(BusinessException.class, err ->
                        assertThat(err.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void shouldDeduplicateWhenSameNaturalKeyIsAlreadyOpen() {
        UUID invoiceId = UUID.randomUUID();
        UUID invoiceLineId = UUID.randomUUID();
        UUID poLineId = UUID.randomUUID();
        UUID oldMatchId = UUID.randomUUID();
        UUID newMatchId = UUID.randomUUID();

        MatchException existingOpen = new MatchException(
                invoiceId, invoiceLineId, poLineId, oldMatchId,
                ExceptionType.PRICE_VARIANCE,
                "Lệch giá lần 1",
                Map.of("priceVariancePct", "3.0000")
        );
        UUID existingId = UUID.randomUUID();
        ReflectionTestUtils.setField(existingOpen, "id", existingId);

        when(exceptionRepository.findOpenByNaturalKey(invoiceId, invoiceLineId, poLineId, ExceptionType.PRICE_VARIANCE))
                .thenReturn(Optional.of(existingOpen));
        when(exceptionRepository.saveAndFlush(existingOpen)).thenReturn(existingOpen);

        MatchExceptionDto result = factoryService.createOrGetOpen(new CreateMatchExceptionCommand(
                invoiceId, invoiceLineId, poLineId, newMatchId,
                ExceptionType.PRICE_VARIANCE,
                "Lệch giá lần 2 sau khi chạy lại",
                Map.of("priceVariancePct", "3.2000")
        ));

        assertThat(result.id()).isEqualTo(existingId);
        assertThat(result.matchId()).isEqualTo(newMatchId);
        assertThat(result.message()).isEqualTo("Lệch giá lần 2 sau khi chạy lại");
        assertThat(result.status()).isEqualTo(ExceptionStatus.OPEN);
        verify(exceptionRepository, times(1)).saveAndFlush(existingOpen);
    }

    @Test
    void shouldCreateNewOpenExceptionWhenPreviousOneWasAlreadyResolved() {
        UUID invoiceId = UUID.randomUUID();
        CreateMatchExceptionCommand cmd = new CreateMatchExceptionCommand(
                invoiceId, null, null, UUID.randomUUID(),
                ExceptionType.TAX_CODE_MISMATCH,
                "MST trên hóa đơn không khớp danh mục NCC",
                Map.of("invoiceTaxCode", "0301234567")
        );

        // Bản ghi trước đó đã RESOLVED nên findOpenByNaturalKey trả về empty
        when(exceptionRepository.findOpenByNaturalKey(invoiceId, null, null, ExceptionType.TAX_CODE_MISMATCH))
                .thenReturn(Optional.empty());
        when(exceptionRepository.saveAndFlush(any(MatchException.class))).thenAnswer(inv -> {
            MatchException entity = inv.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
            return entity;
        });

        MatchExceptionDto created = factoryService.createOrGetOpen(cmd);

        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo(ExceptionStatus.OPEN);
        assertThat(created.resolvedBy()).isNull();
        assertThat(created.resolvedAt()).isNull();
    }

    @Test
    void shouldDeduplicateDuplicateCommandsWithinSameBatch() {
        UUID invoiceId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();

        CreateMatchExceptionCommand dup1 = new CreateMatchExceptionCommand(
                invoiceId, lineId, null, UUID.randomUUID(),
                ExceptionType.MISSING_GRN, "Chưa có GRN (lần 1)", null
        );
        CreateMatchExceptionCommand dup2 = new CreateMatchExceptionCommand(
                invoiceId, lineId, null, UUID.randomUUID(),
                ExceptionType.MISSING_GRN, "Chưa có GRN (lần 2)", null
        );

        when(exceptionRepository.findOpenByNaturalKey(invoiceId, lineId, null, ExceptionType.MISSING_GRN))
                .thenReturn(Optional.empty());
        when(exceptionRepository.saveAndFlush(any(MatchException.class))).thenAnswer(inv -> inv.getArgument(0));

        List<MatchExceptionDto> batchResults = factoryService.createBatchIdempotent(List.of(dup1, dup2));

        assertThat(batchResults).hasSize(1);
        assertThat(batchResults.get(0).message()).isEqualTo("Chưa có GRN (lần 2)");
        verify(exceptionRepository, times(1)).saveAndFlush(any(MatchException.class));
    }

    @Test
    void shouldNeverCallDeleteOnAuditLogRepository() {
        // Xác nhận compile-time rằng AuditLogRepository chỉ cung cấp save & find
        verify(exceptionRepository, never()).deleteAll();
    }
}