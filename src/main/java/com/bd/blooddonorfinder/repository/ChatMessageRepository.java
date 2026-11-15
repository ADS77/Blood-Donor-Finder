package com.bd.blooddonorfinder.repository;

import com.bd.blooddonorfinder.model.chat.ChatMessage;
import org.springframework.data.domain.Pageable;
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
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    Optional<ChatMessage> findFirstByChatIdOrderByCreatedAtDesc(UUID chatId);

    /** Backfill: messages created after the cursor (client dedupes by id on ties). */
    @Query("""
           SELECT m FROM ChatMessage m
           WHERE m.chat.id = :chatId AND m.createdAt >= :after
           ORDER BY m.createdAt ASC
           """)
    List<ChatMessage> findAfter(@Param("chatId") UUID chatId,
                                @Param("after") LocalDateTime after,
                                Pageable pageable);

    /** Latest page for a first load (fetched descending, reversed by the caller). */
    @Query("""
           SELECT m FROM ChatMessage m
           WHERE m.chat.id = :chatId
           ORDER BY m.createdAt DESC
           """)
    List<ChatMessage> findLatest(@Param("chatId") UUID chatId, Pageable pageable);

    /** Unread messages in a chat that were NOT sent by the given user. */
    @Query("""
           SELECT COUNT(m) FROM ChatMessage m
           WHERE m.chat.id = :chatId AND m.sender.id <> :userId AND m.readAt IS NULL
           """)
    long countUnread(@Param("chatId") UUID chatId, @Param("userId") UUID userId);

    /** Marks every counterpart message in the chat as read. */
    @Transactional
    @Modifying
    @Query("""
           UPDATE ChatMessage m SET m.readAt = :readAt
           WHERE m.chat.id = :chatId AND m.sender.id <> :userId AND m.readAt IS NULL
           """)
    int markAllRead(@Param("chatId") UUID chatId,
                    @Param("userId") UUID userId,
                    @Param("readAt") LocalDateTime readAt);
}
