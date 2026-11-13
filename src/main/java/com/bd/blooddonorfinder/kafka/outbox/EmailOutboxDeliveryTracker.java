package com.bd.blooddonorfinder.kafka.outbox;

import com.bd.blooddonorfinder.model.enums.EmailOutboxStatus;
import com.bd.blooddonorfinder.repository.EmailOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;

/**
 * Closes the feedback loop from the email consumer back to the outbox row.
 *
 * <p>An event can leave the relay as PUBLISHED before the consumer finishes,
 * so these updates are conditional: a state transition never overwrites a
 * more advanced state.</p>
 */
@Service
@Slf4j
public class EmailOutboxDeliveryTracker {

    private static final int MAX_ERROR_LENGTH = 4000;
    private static final EnumSet<EmailOutboxStatus> ACTIVE_STATES =
            EnumSet.of(EmailOutboxStatus.PROCESSING, EmailOutboxStatus.PUBLISHED);

    private final EmailOutboxRepository repository;

    public EmailOutboxDeliveryTracker(EmailOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void markDelivered(String eventId) {
        int updated = repository.markDeliveredByEventId(
                eventId,
                EmailOutboxStatus.DELIVERED,
                ACTIVE_STATES,
                Instant.now());
        if (updated == 0) {
            log.debug("No active email outbox row to mark DELIVERED for eventId={}", eventId);
        }
    }

    @Transactional
    public void markFailed(String eventId, String error) {
        int updated = repository.markFailedByEventId(
                eventId,
                EmailOutboxStatus.FAILED,
                ACTIVE_STATES,
                truncate(error),
                Instant.now());
        if (updated == 0) {
            log.debug("No active email outbox row to mark FAILED for eventId={}", eventId);
        }
    }

    private String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
