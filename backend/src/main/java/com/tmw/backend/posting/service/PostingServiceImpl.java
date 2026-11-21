package com.tmw.backend.posting.service;

import com.tmw.backend.allocation.api.CapacityAllocationService;
import com.tmw.backend.common.enums.CurrencyCode;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.invoice.api.InvoiceStatus;
import com.tmw.backend.matching.api.AllocationState;
import com.tmw.backend.matching.api.MatchLifecycleApi;
import com.tmw.backend.matching.api.MatchProposal;
import com.tmw.backend.matching.api.MatchStatus;
import com.tmw.backend.posting.api.ActorPrincipal;
import com.tmw.backend.posting.api.PostingResult;
import com.tmw.backend.posting.entity.JournalEntry;
import com.tmw.backend.posting.mapper.PostingMapper;
import com.tmw.backend.posting.repository.JournalEntryRepository;
import com.tmw.backend.posting.sod.SoDGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Scaffold implementation of {@link PostingService} establishing the single atomic
 * transaction boundary for Phase 4 posting flow:
 * 1. Enforce dual SoD check (poster != proposer && poster != approver).
 * 2. Verify Match is in APPROVED state and not already posted.
 * 3. Confirm PO capacity allocations (sorted by po_line_id ASC to prevent deadlocks).
 * 4. Transition Match -> POSTED and Invoice -> POSTED atomically via MatchLifecycleApi.
 * 5. Persist immutable JournalEntry (payment_instruction_id left null as extension slot).
 * 6. Return mapped PostingResult.
 */
@Service
@RequiredArgsConstructor
public class PostingServiceImpl implements PostingService {

    private static final DateTimeFormatter ENTRY_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final MatchLifecycleApi matchLifecycleApi;
    private final SoDGuard sodGuard;
    private final CapacityAllocationService capacityAllocationService;
    private final JournalEntryRepository journalEntryRepository;
    private final PostingMapper postingMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostingResult post(UUID matchId, ActorPrincipal actor, PostingCommand command) {
        validateRequest(matchId, actor);
        if (command == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thiếu thông tin chứng từ hạch toán (PostingCommand)");
        }

        MatchProposal proposal = matchLifecycleApi.getMatch(matchId);
        sodGuard.checkPost(proposal, actor);
        validatePostingPreconditions(proposal);

        confirmReservedAllocations(proposal);

        MatchProposal postedMatch = matchLifecycleApi.markPosted(matchId, actor.userId());

        LocalDate postingDate = command.postingDate() != null
                ? command.postingDate()
                : LocalDate.now(ZoneOffset.UTC);
        CurrencyCode currency = command.currency() != null ? command.currency() : CurrencyCode.VND;
        BigDecimal totalAmount = resolveTotalAmount(command, proposal);
        BigDecimal vatAmount = command.vatAmount() != null ? command.vatAmount() : BigDecimal.ZERO;

        JournalEntry journalEntry = new JournalEntry(
                generateEntryNumber(postingDate),
                postedMatch.id(),
                postedMatch.invoiceId(),
                command.supplierId(),
                postingDate,
                currency,
                totalAmount,
                vatAmount,
                actor.userId(),
                command.description()
        );
        JournalEntry savedEntry = journalEntryRepository.saveAndFlush(journalEntry);

        return postingMapper.toPostingResult(savedEntry, postedMatch.status(), InvoiceStatus.POSTED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostingResult post(UUID matchId, ActorPrincipal actor) {
        validateRequest(matchId, actor);

        MatchProposal proposal = matchLifecycleApi.getMatch(matchId);
        sodGuard.checkPost(proposal, actor);
        validatePostingPreconditions(proposal);

        throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "Cần truyền PostingCommand chứa thông tin tài chính hóa đơn (sẽ tự động truy vấn Invoice trong Giai đoạn 4)"
        );
    }

    private void validatePostingPreconditions(MatchProposal proposal) {
        if (proposal.status() != MatchStatus.APPROVED) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    String.format(
                            "Chỉ có thể hạch toán Match đang ở trạng thái APPROVED (hiện tại: %s)",
                            proposal.status()
                    )
            );
        }
        if (journalEntryRepository.existsByMatchId(proposal.id())) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Match " + proposal.id() + " đã có bút toán ghi sổ (JournalEntry)"
            );
        }
    }

    private void confirmReservedAllocations(MatchProposal proposal) {
        if (proposal.allocationState() != AllocationState.RESERVED
                || proposal.lines() == null
                || proposal.lines().isEmpty()) {
            return;
        }

        // Khóa dòng po_line_capacity theo po_line_id tăng dần để chống deadlock
        List<MatchProposal.MatchLineProposal> sortedLines = proposal.lines().stream()
                .filter(line -> line.poLineId() != null)
                .sorted(Comparator.comparing(MatchProposal.MatchLineProposal::poLineId))
                .toList();

        for (MatchProposal.MatchLineProposal line : sortedLines) {
            capacityAllocationService.confirm(line.poLineId(), line.matchedQty(), line.matchedAmount());
        }
    }

    private static BigDecimal resolveTotalAmount(PostingCommand command, MatchProposal proposal) {
        if (command.totalAmount() != null) {
            return command.totalAmount();
        }
        if (proposal.lines() == null || proposal.lines().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return proposal.lines().stream()
                .map(MatchProposal.MatchLineProposal::matchedAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String generateEntryNumber(LocalDate postingDate) {
        String datePart = postingDate.format(ENTRY_DATE_FORMAT);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return "JE-" + datePart + "-" + suffix;
    }

    private static void validateRequest(UUID matchId, ActorPrincipal actor) {
        if (matchId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "matchId không được để trống");
        }
        if (actor == null || actor.userId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thông tin người thực hiện (actor) không hợp lệ");
        }
    }
}
