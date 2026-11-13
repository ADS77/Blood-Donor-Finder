package com.bd.blooddonorfinder.kafka.outbox;

import com.bd.blooddonorfinder.model.EmailOutboxMessage;
import com.bd.blooddonorfinder.model.enums.EmailOutboxStatus;
import com.bd.blooddonorfinder.repository.EmailOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailOutboxRelayServiceTest {

    @Mock
    private EmailOutboxRepository repository;

    private EmailOutboxRelayService relayService;

    @BeforeEach
    void setUp() {
        relayService = new EmailOutboxRelayService(
                repository, 3, Duration.ofSeconds(5), Duration.ofMinutes(1));
    }

    @Test
    void claimBatch_marksRowsProcessingAndIncrementsAttempts() {
        EmailOutboxMessage message = outboxMessage();
        when(repository.lockPendingBatch(any(Instant.class), eq(10)))
                .thenReturn(List.of(message));

        List<EmailOutboxMessage> claimed = relayService.claimBatch(10);

        assertEquals(1, claimed.size());
        assertEquals(EmailOutboxStatus.PROCESSING, message.getStatus());
        assertEquals(1, message.getAttempts());
        assertNotNull(message.getLockedAt());
        assertNotNull(message.getUpdatedAt());
    }

    @Test
    void claimBatch_skipsRepositoryForNonPositiveBatchSize() {
        assertTrue(relayService.claimBatch(0).isEmpty());

        verify(repository, never()).lockPendingBatch(any(), anyInt());
    }

    @Test
    void markFailedOrRetry_usesExponentialBackoffBeforeBudgetIsExhausted() {
        UUID id = UUID.randomUUID();
        Instant before = Instant.now();

        relayService.markFailedOrRetry(id, 2, "broker unavailable");

        ArgumentCaptor<Instant> nextAttempt = ArgumentCaptor.forClass(Instant.class);
        verify(repository).markPendingForRetry(
                eq(id),
                eq(EmailOutboxStatus.PENDING),
                eq(EmailOutboxStatus.PROCESSING),
                nextAttempt.capture(),
                eq("broker unavailable"),
                any(Instant.class));

        // attempt 2 -> initial backoff * 2 = 10 seconds
        Instant expected = before.plusSeconds(10);
        assertFalse(nextAttempt.getValue().isBefore(expected.minusSeconds(1)));
        assertTrue(nextAttempt.getValue().isBefore(expected.plusSeconds(3)));
    }

    @Test
    void markFailedOrRetry_parksRowWhenAttemptBudgetIsExhausted() {
        UUID id = UUID.randomUUID();

        relayService.markFailedOrRetry(id, 3, "still failing");

        verify(repository).markFailedIfProcessing(
                eq(id),
                eq(EmailOutboxStatus.FAILED),
                eq(EmailOutboxStatus.PROCESSING),
                eq("still failing"),
                any(Instant.class));
        verify(repository, never()).markPendingForRetry(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void markPublished_reportsWhetherTransitionApplied() {
        UUID id = UUID.randomUUID();
        when(repository.markPublished(eq(id), eq(EmailOutboxStatus.PUBLISHED),
                eq(EmailOutboxStatus.PROCESSING), any(Instant.class))).thenReturn(1);

        assertTrue(relayService.markPublished(id));

        when(repository.markPublished(eq(id), eq(EmailOutboxStatus.PUBLISHED),
                eq(EmailOutboxStatus.PROCESSING), any(Instant.class))).thenReturn(0);
        assertFalse(relayService.markPublished(id));
    }

    @Test
    void deleteTerminalBefore_onlyRemovesPublishedAndDeliveredRows() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(7));

        relayService.deleteTerminalBefore(cutoff);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<EmailOutboxStatus>> statuses =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(repository).deleteTerminalBefore(statuses.capture(), eq(cutoff));
        assertTrue(statuses.getValue().contains(EmailOutboxStatus.PUBLISHED));
        assertTrue(statuses.getValue().contains(EmailOutboxStatus.DELIVERED));
        assertFalse(statuses.getValue().contains(EmailOutboxStatus.FAILED));
    }

    private EmailOutboxMessage outboxMessage() {
        EmailOutboxMessage message = new EmailOutboxMessage();
        message.setEventId(UUID.randomUUID().toString());
        message.setStatus(EmailOutboxStatus.PENDING);
        message.setAttempts(0);
        return message;
    }
}
