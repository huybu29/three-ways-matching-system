package com.tmw.backend.matchexception.service;

import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import com.tmw.backend.matchexception.api.CreateMatchExceptionCommand;
import com.tmw.backend.matchexception.api.ExceptionFactoryService;
import com.tmw.backend.matchexception.api.ExceptionStatus;
import com.tmw.backend.matchexception.api.ExceptionType;
import com.tmw.backend.matchexception.api.MatchExceptionDto;
import com.tmw.backend.matchexception.entity.MatchException;
import com.tmw.backend.matchexception.repository.MatchExceptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExceptionFactoryServiceImpl implements ExceptionFactoryService {

    private static final UUID SYSTEM_ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final MatchExceptionRepository exceptionRepository;

    @Override
    @Transactional
    public MatchExceptionDto createOrGetOpen(CreateMatchExceptionCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "CreateMatchExceptionCommand không được null");
        }

        Optional<MatchException> existingOpen = exceptionRepository.findOpenByNaturalKey(
                command.invoiceId(),
                command.invoiceLineId(),
                command.poLineId(),
                command.exceptionType()
        );

        if (existingOpen.isPresent()) {
            MatchException current = existingOpen.get();
            current.refreshOpenContext(command.matchId(), command.message(), command.details());
            return exceptionRepository.saveAndFlush(current).toDto();
        }

        try {
            MatchException created = new MatchException(
                    command.invoiceId(),
                    command.invoiceLineId(),
                    command.poLineId(),
                    command.matchId(),
                    command.exceptionType(),
                    command.message(),
                    command.details()
            );
            return exceptionRepository.saveAndFlush(created).toDto();
        } catch (DataIntegrityViolationException raceConditionEx) {
            return exceptionRepository.findOpenByNaturalKey(
                            command.invoiceId(),
                            command.invoiceLineId(),
                            command.poLineId(),
                            command.exceptionType()
                    )
                    .map(MatchException::toDto)
                    .orElseThrow(() -> raceConditionEx);
        }
    }

    @Override
    @Transactional
    public List<MatchExceptionDto> createBatchIdempotent(List<CreateMatchExceptionCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }

        Map<DeduplicationKey, CreateMatchExceptionCommand> deduplicated = new LinkedHashMap<>();
        for (CreateMatchExceptionCommand cmd : commands) {
            if (cmd != null) {
                DeduplicationKey key = new DeduplicationKey(
                        cmd.invoiceId(),
                        cmd.invoiceLineId(),
                        cmd.poLineId(),
                        cmd.exceptionType()
                );
                deduplicated.put(key, cmd);
            }
        }

        List<MatchExceptionDto> results = new ArrayList<>(deduplicated.size());
        for (CreateMatchExceptionCommand uniqueCmd : deduplicated.values()) {
            results.add(createOrGetOpen(uniqueCmd));
        }
        return results;
    }

    @Override
    @Transactional
    public int autoCloseOpenExceptions(UUID invoiceId, UUID actorId, String reason) {
        if (invoiceId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invoiceId không được để trống");
        }
        List<MatchException> openList =
                exceptionRepository.findAllByInvoiceIdAndStatus(invoiceId, ExceptionStatus.OPEN);
        if (openList.isEmpty()) {
            return 0;
        }

        String closeNote = (reason == null || reason.isBlank())
                ? "Tự động đóng ngoại lệ do hoàn tác hoặc hủy hóa đơn"
                : reason.trim();

        UUID effectiveActorId = (actorId != null) ? actorId : SYSTEM_ACTOR_ID;

        for (MatchException ex : openList) {
            ex.waive(effectiveActorId, closeNote);
        }
        exceptionRepository.saveAllAndFlush(openList);
        return openList.size();
    }

    private record DeduplicationKey(
            UUID invoiceId,
            UUID invoiceLineId,
            UUID poLineId,
            ExceptionType exceptionType
    ) {
    }
}