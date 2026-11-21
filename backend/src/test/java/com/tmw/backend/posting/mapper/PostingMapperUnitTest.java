package com.tmw.backend.posting.mapper;

import com.tmw.backend.common.enums.CurrencyCode;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.invoice.api.InvoiceStatus;
import com.tmw.backend.matching.api.AllocationState;
import com.tmw.backend.matching.api.MatchProposal;
import com.tmw.backend.matching.api.MatchSource;
import com.tmw.backend.matching.api.MatchStatus;
import com.tmw.backend.posting.api.PostingResult;
import com.tmw.backend.posting.dto.JournalEntryResponse;
import com.tmw.backend.posting.dto.PostingResultResponse;
import com.tmw.backend.posting.entity.JournalEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostingMapperUnitTest {

    private PostingMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PostingMapper();
    }

    @Test
    void shouldMapJournalEntryToPostingResultWithNullAndAttachedPaymentInstruction() {
        UUID entryId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID posterId = UUID.randomUUID();
        LocalDate postingDate = LocalDate.of(2026, 11, 21);

        JournalEntry entry = new JournalEntry(
                "je-202611-0001",
                matchId,
                invoiceId,
                supplierId,
                postingDate,
                CurrencyCode.VND,
                new BigDecimal("11000000"),
                new BigDecimal("1000000"),
                posterId,
                "Hạch toán hóa đơn đầu vào"
        );
        ReflectionTestUtils.setField(entry, "id", entryId);

        PostingResult withoutPayment = mapper.toPostingResult(entry, MatchStatus.POSTED, InvoiceStatus.POSTED);
        assertThat(withoutPayment.journalEntryId()).isEqualTo(entryId);
        assertThat(withoutPayment.entryNumber()).isEqualTo("JE-202611-0001");
        assertThat(withoutPayment.matchId()).isEqualTo(matchId);
        assertThat(withoutPayment.invoiceId()).isEqualTo(invoiceId);
        assertThat(withoutPayment.matchStatus()).isEqualTo(MatchStatus.POSTED);
        assertThat(withoutPayment.invoiceStatus()).isEqualTo(InvoiceStatus.POSTED);
        assertThat(withoutPayment.postingDate()).isEqualTo(postingDate);
        assertThat(withoutPayment.currency()).isEqualTo(CurrencyCode.VND);
        assertThat(withoutPayment.postedAmount()).isEqualByComparingTo("11000000.0000");
        assertThat(withoutPayment.postedAmount().scale()).isEqualTo(4);
        assertThat(withoutPayment.paymentInstructionId()).isNull();
        assertThat(withoutPayment.postedBy()).isEqualTo(posterId);
        assertThat(withoutPayment.postedAt()).isNotNull();

        UUID paymentInstructionId = UUID.randomUUID();
        entry.attachPaymentInstruction(paymentInstructionId);

        PostingResult withPayment = mapper.toPostingResult(entry, MatchStatus.POSTED, InvoiceStatus.POSTED);
        assertThat(withPayment.paymentInstructionId()).isEqualTo(paymentInstructionId);
    }

    @Test
    void shouldMapJournalEntryAndProposalToPostingResultResponseAndJournalEntryResponse() {
        UUID entryId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID posterId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        JournalEntry entry = new JournalEntry(
                "JE-202611-0002",
                matchId,
                invoiceId,
                UUID.randomUUID(),
                LocalDate.of(2026, 11, 21),
                CurrencyCode.VND,
                new BigDecimal("5500000.0000"),
                new BigDecimal("500000.0000"),
                posterId,
                "Bút toán tự động"
        );
        ReflectionTestUtils.setField(entry, "id", entryId);
        ReflectionTestUtils.setField(entry, "createdAt", Instant.now());
        entry.attachPaymentInstruction(paymentId);

        MatchProposal proposal = new MatchProposal(
                matchId,
                invoiceId,
                MatchStatus.POSTED,
                MatchSource.AUTO,
                AllocationState.ALLOCATED,
                new BigDecimal("0.9800"),
                UUID.randomUUID(),
                false,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                posterId,
                2L,
                List.of()
        );

        PostingResultResponse fromEntity = mapper.toPostingResultResponse(entry, proposal);
        assertThat(fromEntity.getJournalEntryId()).isEqualTo("JE-202611-0002");
        assertThat(fromEntity.getPaymentInstructionId()).isEqualTo(paymentId.toString());
        assertThat(fromEntity.getPostedAt()).isEqualTo(entry.getPostedAt());
        assertThat(fromEntity.getMatch()).isNotNull();
        assertThat(fromEntity.getMatch().id()).isEqualTo(matchId);
        assertThat(fromEntity.getMatch().status()).isEqualTo(MatchStatus.POSTED);

        PostingResult resultRecord = mapper.toPostingResult(entry, MatchStatus.POSTED, InvoiceStatus.POSTED);
        PostingResultResponse fromRecord = mapper.toPostingResultResponse(resultRecord, proposal);
        assertThat(fromRecord.getJournalEntryId()).isEqualTo("JE-202611-0002");
        assertThat(fromRecord.getPaymentInstructionId()).isEqualTo(paymentId.toString());
        assertThat(fromRecord.getMatch().id()).isEqualTo(matchId);

        JournalEntryResponse detail = mapper.toJournalEntryResponse(entry);
        assertThat(detail.getId()).isEqualTo(entryId);
        assertThat(detail.getJournalEntryId()).isEqualTo("JE-202611-0002");
        assertThat(detail.getMatchId()).isEqualTo(matchId);
        assertThat(detail.getTotalAmount()).isEqualByComparingTo("5500000.0000");
        assertThat(detail.getVatAmount()).isEqualByComparingTo("500000.0000");
        assertThat(detail.getPaymentInstructionId()).isEqualTo(paymentId.toString());
        assertThat(detail.getPostedBy()).isEqualTo(posterId);
    }

    @Test
    void shouldRejectNullInputsWithValidationError() {
        assertThatThrownBy(() -> mapper.toPostingResult(null, MatchStatus.POSTED, InvoiceStatus.POSTED))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThatThrownBy(() -> mapper.toPostingResultResponse((JournalEntry) null, (MatchProposal) null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThatThrownBy(() -> mapper.toPostingResultResponse((PostingResult) null, null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThatThrownBy(() -> mapper.toJournalEntryResponse(null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
