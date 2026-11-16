package com.bd.blooddonorfinder.repository;

import com.bd.blooddonorfinder.model.notification.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** Notification feed, newest first (keyset: everything older than the cursor). */
    // Two variants instead of a nullable cursor parameter; see
    // DocumentRepository.findActiveFeed for the reason.
    @Query("""
           SELECT n FROM Notification n
           WHERE n.recipient.id = :userId
           ORDER BY n.createdAt DESC
           """)
    List<Notification> findFeed(@Param("userId") UUID userId,
                                Pageable pageable);

    @Query("""
           SELECT n FROM Notification n
           WHERE n.recipient.id = :userId
             AND n.createdAt < :before
           ORDER BY n.createdAt DESC
           """)
    List<Notification> findFeedBefore(@Param("userId") UUID userId,
                                      @Param("before") LocalDateTime before,
                                      Pageable pageable);

    @Query("""
           SELECT COUNT(n) FROM Notification n
           WHERE n.recipient.id = :userId AND n.readAt IS NULL
           """)
    long countUnread(@Param("userId") UUID userId);

    @Transactional
    @Modifying
    @Query("""
           UPDATE Notification n SET n.readAt = :readAt
           WHERE n.recipient.id = :userId AND n.readAt IS NULL
           """)
    int markAllRead(@Param("userId") UUID userId, @Param("readAt") LocalDateTime readAt);

    // ── digest worker ───────────────────────────────────────────────────

    /**
     * Users with unread notifications that have never been digested.
     *
     * <p>Keyset-paginated by recipient id (stable, no duplicates) and excludes
     * users still inside the digest cooldown. Without the cursor and cooldown
     * filter a batch of skipped users would be fetched forever.</p>
     */
    @Query(value = """
           SELECT DISTINCT n.recipient_id FROM notification n
           WHERE n.read_at IS NULL AND n.email_digest_at IS NULL AND n.created_at < :cutoff
             AND NOT EXISTS (
                 SELECT 1 FROM notification d
                 WHERE d.recipient_id = n.recipient_id AND d.email_digest_at > :cooldownCutoff
             )
             AND (CAST(:afterId AS uuid) IS NULL OR n.recipient_id > CAST(:afterId AS uuid))
           ORDER BY n.recipient_id
           LIMIT :limit
           """, nativeQuery = true)
    List<UUID> findDigestCandidateUserIds(@Param("cutoff") LocalDateTime cutoff,
                                          @Param("cooldownCutoff") LocalDateTime cooldownCutoff,
                                          @Param("afterId") UUID afterId,
                                          @Param("limit") int limit);

    /** The user's oldest undigested unread notifications (top of the digest email). */
    @Query("""
           SELECT n FROM Notification n
           WHERE n.recipient.id = :userId
             AND n.readAt IS NULL AND n.emailDigestAt IS NULL
           ORDER BY n.createdAt ASC
           """)
    List<Notification> findUndigestedUnread(@Param("userId") UUID userId, Pageable pageable);

    @Transactional
    @Modifying
    @Query("""
           UPDATE Notification n SET n.emailDigestAt = :digestedAt
           WHERE n.id IN :ids
           """)
    int markDigested(@Param("ids") List<UUID> ids, @Param("digestedAt") LocalDateTime digestedAt);
}
