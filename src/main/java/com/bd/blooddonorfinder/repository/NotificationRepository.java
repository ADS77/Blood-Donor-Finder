package com.bd.blooddonorfinder.repository;

import com.bd.blooddonorfinder.model.notification.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** Notification feed, newest first (keyset: everything older than the cursor). */
    @Query("""
           SELECT n FROM Notification n
           WHERE n.recipient.id = :userId
             AND (:before IS NULL OR n.createdAt < :before)
           ORDER BY n.createdAt DESC
           """)
    List<Notification> findFeed(@Param("userId") UUID userId,
                                @Param("before") LocalDateTime before,
                                Pageable pageable);

    @Query("""
           SELECT COUNT(n) FROM Notification n
           WHERE n.recipient.id = :userId AND n.readAt IS NULL
           """)
    long countUnread(@Param("userId") UUID userId);

    @Modifying
    @Query("""
           UPDATE Notification n SET n.readAt = :readAt
           WHERE n.recipient.id = :userId AND n.readAt IS NULL
           """)
    int markAllRead(@Param("userId") UUID userId, @Param("readAt") LocalDateTime readAt);

    // ── digest worker ───────────────────────────────────────────────────

    /** Users with unread notifications that have never been digested. */
    @Query(value = """
           SELECT DISTINCT n.recipient_id FROM notification n
           WHERE n.read_at IS NULL AND n.email_digest_at IS NULL AND n.created_at < :cutoff
           LIMIT :limit
           """, nativeQuery = true)
    List<UUID> findDigestCandidateUserIds(@Param("cutoff") LocalDateTime cutoff,
                                          @Param("limit") int limit);

    /** The user's oldest undigested unread notifications (top of the digest email). */
    @Query("""
           SELECT n FROM Notification n
           WHERE n.recipient.id = :userId
             AND n.readAt IS NULL AND n.emailDigestAt IS NULL
           ORDER BY n.createdAt ASC
           """)
    List<Notification> findUndigestedUnread(@Param("userId") UUID userId, Pageable pageable);

    /** True when the user already received a digest within the cooldown window. */
    @Query("""
           SELECT COUNT(n) > 0 FROM Notification n
           WHERE n.recipient.id = :userId AND n.emailDigestAt > :cooldownCutoff
           """)
    boolean digestedSince(@Param("userId") UUID userId, @Param("cooldownCutoff") LocalDateTime cooldownCutoff);

    @Modifying
    @Query("""
           UPDATE Notification n SET n.emailDigestAt = :digestedAt
           WHERE n.id IN :ids
           """)
    int markDigested(@Param("ids") List<UUID> ids, @Param("digestedAt") LocalDateTime digestedAt);
}
