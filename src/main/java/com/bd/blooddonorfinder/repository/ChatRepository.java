package com.bd.blooddonorfinder.repository;

import com.bd.blooddonorfinder.model.chat.Chat;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatRepository extends JpaRepository<Chat, UUID> {

    boolean existsByRequestId(UUID requestId);

    /**
     * Idempotent conversation creation. The unique constraint on request_id is
     * the arbiter, so concurrent opens by the requester and donor converge on
     * a single row instead of racing a find-then-insert. The insert is also
     * conditional on the request still being chat-eligible, so a concurrent
     * completion/expiry cannot open a conversation for a closed request.
     */
    @Transactional
    @Modifying
    @Query(value = """
           INSERT INTO chat (id, request_id, requester_id, donor_id, status, created_at)
           SELECT :id, :requestId, :requesterId, :donorId, 'ACTIVE', :createdAt
           WHERE EXISTS (
               SELECT 1 FROM blood_request r
               WHERE r.id = :requestId
                 AND r.status IN ('ACCEPTED', 'PENDING_APPROVAL', 'DISPUTED')
           )
           ON CONFLICT (request_id) DO NOTHING
           """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("requestId") UUID requestId,
                       @Param("requesterId") UUID requesterId,
                       @Param("donorId") UUID donorId,
                       @Param("createdAt") LocalDateTime createdAt);

    @EntityGraph(attributePaths = {"request", "requester", "donor"})
    Optional<Chat> findByRequestId(UUID requestId);

    @EntityGraph(attributePaths = {"request", "requester", "donor"})
    Optional<Chat> findWithParticipantsById(UUID id);

    /** All chats where the user is either the requester or the accepted donor. */
    @Query("""
           SELECT c FROM Chat c
           WHERE c.requester.id = :userId OR c.donor.id = :userId
           ORDER BY c.createdAt DESC
           """)
    @EntityGraph(attributePaths = {"request", "requester", "donor"})
    List<Chat> findMyChats(@Param("userId") UUID userId, Pageable pageable);
}
