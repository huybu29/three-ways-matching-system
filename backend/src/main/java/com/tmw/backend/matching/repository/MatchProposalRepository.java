package com.tmw.backend.matching.repository;

import com.tmw.backend.matching.entity.MatchProposal;
import com.tmw.backend.matching.entity.MatchStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchProposalRepository extends JpaRepository<MatchProposal, UUID> {

    /** Lấy kèm lines cho GET /matches/{matchId}. */
    @EntityGraph(attributePaths = "lines")
    Optional<MatchProposal> findWithLinesById(UUID id);

    List<MatchProposal> findByInvoiceIdOrderByProposedAtDesc(UUID invoiceId);

    /** Kiểm tra hóa đơn đã có đề xuất còn hiệu lực (PROPOSED/APPROVED) chưa. */
    boolean existsByInvoiceIdAndStatusIn(UUID invoiceId, Collection<MatchStatus> statuses);
}
