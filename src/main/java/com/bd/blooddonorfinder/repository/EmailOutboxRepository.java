package com.bd.blooddonorfinder.repository;

import com.bd.blooddonorfinder.model.EmailOutboxMessage;
import com.bd.blooddonorfinder.model.enums.EmailOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface EmailOutboxRepository extends JpaRepository<EmailOutboxMessage, UUID> {

    /**
     * Claims the oldest due rows without blocking another relay instance.
     * Must be called inside a short transaction; the caller flips the rows to
     * PROCESSING before committing, so the row locks are not held during the
     * network call to Kafka.
     */
    @Query(
            value = """
                    SELECT *
                    FROM email_outbox
                    WHERE status = 'PENDING'
                      AND next_attempt_at <= :now
                    ORDER BY created_at ASC
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    List<EmailOutboxMessage> lockPendingBatch(@Param("now") Instant now,
                                              @Param("batchSize") int batchSize);

    /**
     * Returns rows owned by a relay instance that stopped before writing a
     * terminal state back to the database.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EmailOutboxMessage m
               SET m.status = :pendingStatus,
                   m.updatedAt = :now,
                   m.lockedAt = NULL,
                   m.nextAttemptAt = :now
             WHERE m.status = :processingStatus
               AND m.lockedAt < :staleBefore
            """)
    int reclaimStaleClaims(@Param("pendingStatus") EmailOutboxStatus pendingStatus,
                           @Param("processingStatus") EmailOutboxStatus processingStatus,
                           @Param("staleBefore") Instant staleBefore,
                           @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EmailOutboxMessage m
               SET m.status = :publishedStatus,
                   m.updatedAt = :now,
                   m.publishedAt = :now,
                   m.lockedAt = NULL,
                   m.lastError = NULL
             WHERE m.id = :id
               AND m.status = :processingStatus
            """)
    int markPublished(@Param("id") UUID id,
                      @Param("publishedStatus") EmailOutboxStatus publishedStatus,
                      @Param("processingStatus") EmailOutboxStatus processingStatus,
                      @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EmailOutboxMessage m
               SET m.status = :pendingStatus,
                   m.updatedAt = :now,
                   m.lockedAt = NULL,
                   m.nextAttemptAt = :nextAttemptAt,
                   m.lastError = :error
             WHERE m.id = :id
               AND m.status = :processingStatus
            """)
    int markPendingForRetry(@Param("id") UUID id,
                            @Param("pendingStatus") EmailOutboxStatus pendingStatus,
                            @Param("processingStatus") EmailOutboxStatus processingStatus,
                            @Param("nextAttemptAt") Instant nextAttemptAt,
                            @Param("error") String error,
                            @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EmailOutboxMessage m
               SET m.status = :failedStatus,
                   m.updatedAt = :now,
                   m.lockedAt = NULL,
                   m.lastError = :error
             WHERE m.id = :id
               AND m.status = :processingStatus
            """)
    int markFailedIfProcessing(@Param("id") UUID id,
                               @Param("failedStatus") EmailOutboxStatus failedStatus,
                               @Param("processingStatus") EmailOutboxStatus processingStatus,
                               @Param("error") String error,
                               @Param("now") Instant now);

    /**
     * Recorded by the consumer after SMTP accepted the message. Guarded by the
     * current state because a fast consumer can complete before the relay
     * writes PUBLISHED; in that case the row must not regress.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EmailOutboxMessage m
               SET m.status = :deliveredStatus,
                   m.updatedAt = :now
             WHERE m.eventId = :eventId
               AND m.status IN :fromStatuses
            """)
    int markDeliveredByEventId(@Param("eventId") String eventId,
                               @Param("deliveredStatus") EmailOutboxStatus deliveredStatus,
                               @Param("fromStatuses") Collection<EmailOutboxStatus> fromStatuses,
                               @Param("now") Instant now);

    /**
     * Recorded by the consumer once the retry budget is exhausted and the
     * event has been handed to the dead-letter topic.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EmailOutboxMessage m
               SET m.status = :failedStatus,
                   m.updatedAt = :now,
                   m.lastError = :error
             WHERE m.eventId = :eventId
               AND m.status IN :fromStatuses
            """)
    int markFailedByEventId(@Param("eventId") String eventId,
                            @Param("failedStatus") EmailOutboxStatus failedStatus,
                            @Param("fromStatuses") Collection<EmailOutboxStatus> fromStatuses,
                            @Param("error") String error,
                            @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM EmailOutboxMessage m
             WHERE m.status IN :statuses
               AND m.updatedAt < :cutoff
            """)
    int deleteTerminalBefore(@Param("statuses") Collection<EmailOutboxStatus> statuses,
                             @Param("cutoff") Instant cutoff);
}
