package com.tmw.backend.matchexception.service;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.matchexception.api.CreateMatchExceptionCommand;
import com.tmw.backend.matchexception.api.ExceptionStatus;
import com.tmw.backend.matchexception.api.ExceptionType;
import com.tmw.backend.matchexception.api.MatchExceptionDto;
import com.tmw.backend.matchexception.entity.MatchException;
import com.tmw.backend.matchexception.repository.MatchExceptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExceptionFactoryServiceImplTest {

    @Mock
    private MatchExceptionRepository exceptionRepository;

    @InjectMocks
    private ExceptionFactoryServiceImpl service;

    @Test
    void createOrGetOpen_shouldCreateNewOpenExceptionWithJsonDetails() {
        UUID invoiceId = UUID.randomUUID();
        UUID invoiceLineId = UUID.randomUUID();
        UUID poLineId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        Map<String, Object> details = Map.of("expectedQty", 10, "actualQty", 12);

        when(exceptionRepository.findOpenByNaturalKey(invoiceId, invoiceLineId, poLineId, ExceptionType.QTY_VARIANCE))
                .thenReturn(Optional.empty());
        when(exceptionRepository.saveAndFlush(any(MatchException.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateMatchExceptionCommand cmd = new CreateMatchExceptionCommand(
                invoiceId, invoiceLineId, poLineId, matchId,
                ExceptionType.QTY_VARIANCE, "Lệch số lượng", details
        );

        MatchExceptionDto dto = service.createOrGetOpen(cmd);

        assertThat(dto.status()).isEqualTo(ExceptionStatus.OPEN);
        assertThat(dto.exceptionType()).isEqualTo(ExceptionType.QTY_VARIANCE);
        assertThat(dto.details()).containsEntry("expectedQty", 10).containsEntry("actualQty", 12);
        verify(exceptionRepository, times(1)).saveAndFlush(any(MatchException.class));
    }

    @Test
    void createOrGetOpen_shouldRefreshExistingOpenExceptionInsteadOfCreatingDuplicate() {
        UUID invoiceId = UUID.randomUUID();
        UUID invoiceLineId = UUID.randomUUID();
        UUID poLineId = UUID.randomUUID();
        UUID oldMatchId = UUID.randomUUID();
        UUID newMatchId = UUID.randomUUID();

        MatchException existing = new MatchException(
                invoiceId, invoiceLineId, poLineId, oldMatchId,
                ExceptionType.PRICE_VARIANCE, "Lỗi giá cũ", Map.of("diff", 500)
        );

        when(exceptionRepository.findOpenByNaturalKey(invoiceId, invoiceLineId, poLineId, ExceptionType.PRICE_VARIANCE))
                .thenReturn(Optional.of(existing));
        when(exceptionRepository.saveAndFlush(existing)).thenReturn(existing);

        CreateMatchExceptionCommand cmd = new CreateMatchExceptionCommand(
                invoiceId, invoiceLineId, poLineId, newMatchId,
                ExceptionType.PRICE_VARIANCE, "Lỗi giá mới", Map.of("diff", 1200)
        );

        MatchExceptionDto dto = service.createOrGetOpen(cmd);

        assertThat(dto.matchId()).isEqualTo(newMatchId);
        assertThat(dto.message()).isEqualTo("Lỗi giá mới");
        assertThat(dto.details()).containsEntry("diff", 1200);
        verify(exceptionRepository, times(1)).saveAndFlush(existing);
    }

    @Test
    void createBatchIdempotent_shouldDeduplicateSameNaturalKeyInBatch() {
        UUID invoiceId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID poLineId = UUID.randomUUID();

        when(exceptionRepository.findOpenByNaturalKey(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(exceptionRepository.saveAndFlush(any(MatchException.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateMatchExceptionCommand first = new CreateMatchExceptionCommand(
                invoiceId, lineId, poLineId, UUID.randomUUID(),
                ExceptionType.AMOUNT_VARIANCE, "Lệch lần 1", Map.of("step", 1)
        );
        CreateMatchExceptionCommand second = new CreateMatchExceptionCommand(
                invoiceId, lineId, poLineId, UUID.randomUUID(),
                ExceptionType.AMOUNT_VARIANCE, "Lệch lần 2", Map.of("step", 2)
        );

        List<MatchExceptionDto> result = service.createBatchIdempotent(List.of(first, second));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).message()).isEqualTo("Lệch lần 2");
        verify(exceptionRepository, times(1)).saveAndFlush(any(MatchException.class));
    }

    @Test
    void autoCloseOpenExceptions_shouldUseSystemActorWhenActorIdIsNull() {
        UUID invoiceId = UUID.randomUUID();
        MatchException openEx = new MatchException(
                invoiceId, null, null, null,
                ExceptionType.MISSING_PO, "Thiếu PO", null
        );

        when(exceptionRepository.findAllByInvoiceIdAndStatus(invoiceId, ExceptionStatus.OPEN))
                .thenReturn(List.of(openEx));
        when(exceptionRepository.saveAllAndFlush(List.of(openEx))).thenReturn(List.of(openEx));

        int closedCount = service.autoCloseOpenExceptions(invoiceId, null, "Hủy hóa đơn");

        assertThat(closedCount).isEqualTo(1);
        assertThat(openEx.getStatus()).isEqualTo(ExceptionStatus.WAIVED);
        assertThat(openEx.getResolvedBy()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000000"));
        assertThat(openEx.getResolvedAt()).isNotNull();
    }

    @Test
    void createOrGetOpen_shouldThrowValidationErrorWhenCommandIsNull() {
        assertThatThrownBy(() -> service.createOrGetOpen(null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}