package com.bd.blooddonorfinder.kafka.outbox;

import com.bd.blooddonorfinder.model.EmailOutboxMessage;
import com.bd.blooddonorfinder.model.enums.EmailOutboxStatus;
import com.bd.blooddonorfinder.repository.EmailOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Transactional state transitions for the email outbox.
 *
 * <p>Claiming is deliberately separated from publishing: the claim
 * transaction holds PostgreSQL row locks only long enough to flip rows to
 * PROCESSING. The relay then performs the Kafka network call outside any
 * database transaction, so a slow broker cannot pin row locks or exhaust the
 * connection pool.</p>
 */
@Service
@Slf4j
public class EmailOutboxRelayService {

    private static final int MAX_ERROR_LENGTH = 4000;

    private final EmailOutboxRepository repository;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;

    public EmailOutboxRelayService(
            EmailOutboxRepository repository,
            @Value("${email.outbox.relay.max-attempts:10}") int maxAttempts,
            @Value("${email.outbox.relay.initial-backoff:5s}") Duration initialBackoff,
            @Value("${email.outbox.relay.max-backoff:5m}") Duration maxBackoff) {
        this.repository = repository;
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
    }

    /**
     * Claims a batch of due rows by turning them into PROCESSING inside one
     * short transaction. The returned entities are detached once the
     * transaction commits and are safe to hand to the publisher.
     */
    @Transactional
    public List<EmailOutboxMessage> claimBatch(int batchSize) {
        if (batchSize <= 0) {
            return List.of();
        }
        Instant now = Instant.now();
        List<EmailOutboxMessage> batch = repository.lockPendingBatch(now, batchSize);
        for (EmailOutboxMessage message : batch) {
            message.setStatus(EmailOutboxStatus.PROCESSING);
            message.setAttempts(message.getAttempts() + 1);
            message.setLockedAt(now);
            message.setUpdatedAt(now);
        }
        // Managed entities are flushed by dirty checking when this transaction
        // commits, releasing the FOR UPDATE SKIP LOCKED row locks.
        return batch;
    }

    @Transactional
    public int reclaimStaleClaims(Duration staleTimeout) {
        Instant now = Instant.now();
        return repository.reclaimStaleClaims(
                EmailOutboxStatus.PENDING,
                EmailOutboxStatus.PROCESSING,
                now.minus(staleTimeout),
                now);
    }

    @Transactional
    public boolean markPublished(UUID id) {
        int updated = repository.markPublished(
                id,
                EmailOutboxStatus.PUBLISHED,
                EmailOutboxStatus.PROCESSING,
                Instant.now());
        if (updated == 0) {
            // A very fast consumer may already have marked the row DELIVERED,
            // or a stale-claim sweep may have returned it to PENDING.
            log.debug("Outbox row {} was no longer PROCESSING when PUBLISHED was recorded", id);
        }
        return updated > 0;
    }

    /**
     * Applies exponential backoff after a broker or state-transition failure.
     * Once the attempt budget is exhausted the row is parked as FAILED so it
     * can be inspected or replayed without blocking the dispatch index.
     */
    @Transactional
    public void markFailedOrRetry(UUID id, int attempts, String error) {
        Instant now = Instant.now();
        String safeError = truncate(error);

        if (attempts >= maxAttempts) {
            int updated = repository.markFailedIfProcessing(
                    id,
                    EmailOutboxStatus.FAILED,
                    EmailOutboxStatus.PROCESSING,
                    safeError,
                    now);
            if (updated > 0) {
                log.error("Email outbox publication exhausted {} attempt(s); row parked as FAILED: id={}",
                        attempts, id);
            } else {
                log.debug("Outbox row {} already left PROCESSING; FAILED state was not applied", id);
            }
            return;
        }

        Duration delay = backoffFor(attempts);
        int updated = repository.markPendingForRetry(
                id,
                EmailOutboxStatus.PENDING,
                EmailOutboxStatus.PROCESSING,
                now.plus(delay),
                safeError,
                now);
        if (updated > 0) {
            log.warn("Email outbox publication failed; retrying id={} after {} (attempt {}/{})",
                    id, delay, attempts, maxAttempts);
        } else {
            log.debug("Outbox row {} already left PROCESSING; retry state was not applied", id);
        }
    }

    @Transactional
    public void markFailedPermanently(UUID id, String error) {
        int updated = repository.markFailedIfProcessing(
                id,
                EmailOutboxStatus.FAILED,
                EmailOutboxStatus.PROCESSING,
                truncate(error),
                Instant.now());
        if (updated > 0) {
            log.error("Email outbox row parked as FAILED without retry: id={}, cause={}", id, error);
        }
    }

    @Transactional
    public int deleteTerminalBefore(Instant cutoff) {
        return repository.deleteTerminalBefore(
                EnumSet.of(EmailOutboxStatus.PUBLISHED, EmailOutboxStatus.DELIVERED),
                cutoff);
    }

    private Duration backoffFor(int attempts) {
        long multiplier = 1L << Math.min(Math.max(attempts - 1, 0), 20);
        Duration delay = initialBackoff.multipliedBy(multiplier);
        return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
    }

    private String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
