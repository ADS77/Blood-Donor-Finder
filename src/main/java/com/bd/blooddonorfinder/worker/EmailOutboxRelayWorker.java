package com.bd.blooddonorfinder.worker;

import com.bd.blooddonorfinder.exception.KafkaPublishException;
import com.bd.blooddonorfinder.kafka.model.events.EmailNotificationEvent;
import com.bd.blooddonorfinder.kafka.outbox.EmailOutboxRelayService;
import com.bd.blooddonorfinder.kafka.producer.GenericKafkaEventProducer;
import com.bd.blooddonorfinder.model.EmailOutboxMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Scheduled relay that drains PENDING email outbox rows to Kafka.
 *
 * <p>ShedLock keeps a single instance active in a multi-node deployment, while
 * {@code FOR UPDATE SKIP LOCKED} in the repository makes the claim safe even
 * if the lock is lost or multiple relays are intentionally enabled.</p>
 *
 * <p>A row is only marked PUBLISHED after the broker acknowledges the send. If
 * the process dies after the broker acknowledges but before the status update,
 * the row is reclaimed after the stale-claim timeout and published again. That
 * duplicate is acceptable because delivery is at-least-once and the consumer
 * de-duplicates by event id.</p>
 */
@Component
@Slf4j
public class EmailOutboxRelayWorker {

    private final EmailOutboxRelayService relayService;
    private final ObjectMapper objectMapper;
    private final GenericKafkaEventProducer eventProducer;
    private final int batchSize;
    private final Duration staleClaimTimeout;
    private final Duration retention;

    public EmailOutboxRelayWorker(
            EmailOutboxRelayService relayService,
            ObjectMapper objectMapper,
            GenericKafkaEventProducer eventProducer,
            @Value("${email.outbox.relay.batch-size:25}") int batchSize,
            @Value("${email.outbox.relay.stale-claim-timeout:5m}") Duration staleClaimTimeout,
            @Value("${email.outbox.retention:7d}") Duration retention) {
        this.relayService = relayService;
        this.objectMapper = objectMapper;
        this.eventProducer = eventProducer;
        this.batchSize = batchSize;
        this.staleClaimTimeout = staleClaimTimeout;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${email.outbox.relay.poll-interval:5s}")
    @SchedulerLock(name = "emailOutboxRelayWorker", lockAtLeastFor = "PT1S", lockAtMostFor = "PT10M")
    public void relayPendingEmails() {
        LockAssert.assertLocked();

        int reclaimed = relayService.reclaimStaleClaims(staleClaimTimeout);
        if (reclaimed > 0) {
            log.warn("Reclaimed {} stale email outbox claim(s)", reclaimed);
        }

        List<EmailOutboxMessage> batch = relayService.claimBatch(batchSize);
        if (batch.isEmpty()) {
            return;
        }

        log.debug("Relaying {} email notification(s) to Kafka", batch.size());
        for (EmailOutboxMessage message : batch) {
            publishOne(message);
        }
    }

    private void publishOne(EmailOutboxMessage message) {
        EmailNotificationEvent event;
        try {
            event = objectMapper.readValue(message.getPayload(), EmailNotificationEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Malformed email outbox payload: id={}, eventId={}",
                    message.getId(), message.getEventId(), e);
            safelyMarkPermanentlyFailed(message, "Malformed outbox payload: " + e.getMessage());
            return;
        }

        try {
            eventProducer.publishEventBlocking(event);
        } catch (KafkaPublishException e) {
            log.warn("Email outbox publication failed: id={}, eventId={}, attempt={}, cause={}",
                    message.getId(), message.getEventId(), message.getAttempts(), e.getMessage());
            safelyMarkForRetry(message, e.getMessage());
            return;
        }

        try {
            relayService.markPublished(message.getId());
        } catch (RuntimeException e) {
            // The event is already on Kafka. Leaving the row PROCESSING means
            // it will be reclaimed and published again; the consumer's event-id
            // guard makes that duplicate safe.
            log.error("Email event was published but its outbox row could not be updated: id={}, eventId={}",
                    message.getId(), message.getEventId(), e);
        }
    }

    private void safelyMarkForRetry(EmailOutboxMessage message, String error) {
        try {
            relayService.markFailedOrRetry(message.getId(), message.getAttempts(), error);
        } catch (RuntimeException e) {
            log.error("Failed to record retry state for email outbox row: id={}, eventId={}",
                    message.getId(), message.getEventId(), e);
        }
    }

    private void safelyMarkPermanentlyFailed(EmailOutboxMessage message, String error) {
        try {
            relayService.markFailedPermanently(message.getId(), error);
        } catch (RuntimeException e) {
            log.error("Failed to record FAILED state for email outbox row: id={}, eventId={}",
                    message.getId(), message.getEventId(), e);
        }
    }

    @Scheduled(cron = "${email.outbox.cleanup.cron:0 30 2 * * *}")
    @SchedulerLock(name = "emailOutboxCleanupWorker", lockAtLeastFor = "PT30S", lockAtMostFor = "PT30M")
    public void deleteExpiredOutboxRows() {
        LockAssert.assertLocked();
        Instant cutoff = Instant.now().minus(retention);
        int deleted = relayService.deleteTerminalBefore(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} terminal email outbox row(s) older than {}", deleted, cutoff);
        }
    }
}
