package com.bd.blooddonorfinder.repository;

import com.bd.blooddonorfinder.model.chat.Chat;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatRepository extends JpaRepository<Chat, UUID> {

    boolean existsByRequestId(UUID requestId);

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
