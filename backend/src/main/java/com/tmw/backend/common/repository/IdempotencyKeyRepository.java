package com.tmw.backend.common.repository;

import com.tmw.backend.common.entity.IdempotencyKeyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyRecord, Long> {

    Optional<IdempotencyKeyRecord> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Transactional
    @Query("DELETE FROM IdempotencyKeyRecord i WHERE i.expiresAt <= :cutoff")
    int deleteExpiredRecords(@Param("cutoff") Instant cutoff);
}