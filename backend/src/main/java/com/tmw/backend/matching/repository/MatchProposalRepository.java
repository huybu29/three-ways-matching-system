package com.tmw.backend.matching.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.tmw.backend.matching.api.MatchStatus;
import com.tmw.backend.matching.entity.Match;

public interface MatchProposalRepository extends JpaRepository<Match, UUID> {

    /** Lấy kèm lines cho GET /matches/{matchId}. */
    @EntityGraph(attributePaths = "lines")
    Optional<Match> findWithLinesById(UUID id);

    /** Lấy danh sách đối chiếu của hóa đơn theo thời gian tạo mới nhất. */
    List<Match> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

    /** Kiểm tra hóa đơn đã có đề xuất còn hiệu lực (PROPOSED/APPROVED) chưa. */
    boolean existsByInvoiceIdAndStatusIn(UUID invoiceId, Collection<MatchStatus> statuses);
}