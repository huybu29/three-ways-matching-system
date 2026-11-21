package com.tmw.backend.common.scheduler;

import com.tmw.backend.common.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyCleanupJob {

    private final IdempotencyKeyRepository repository;

    @Scheduled(fixedDelay = 3600000L, initialDelay = 60000L)
    public void cleanupExpiredIdempotencyKeys() {
        int deleted = repository.deleteExpiredRecords(Instant.now());
        if (deleted > 0) {
            log.info("Deleted {} expired idempotency key records older than 24h", deleted);
        }
    }
}