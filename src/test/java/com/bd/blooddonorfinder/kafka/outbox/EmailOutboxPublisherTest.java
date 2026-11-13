package com.bd.blooddonorfinder.kafka.outbox;

import com.bd.blooddonorfinder.kafka.model.events.EmailNotificationEvent;
import com.bd.blooddonorfinder.model.EmailOutboxMessage;
import com.bd.blooddonorfinder.model.enums.EmailNotificationType;
import com.bd.blooddonorfinder.model.enums.EmailOutboxStatus;
import com.bd.blooddonorfinder.payload.request.MailAttachment;
import com.bd.blooddonorfinder.payload.request.SendMailRequest;
import com.bd.blooddonorfinder.repository.EmailOutboxRepository;
import com.bd.blooddonorfinder.utils.EmailPayloadCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailOutboxPublisherTest {

    @Mock
    private EmailOutboxRepository repository;

    private ObjectMapper objectMapper;
    private EmailPayloadCipher cipher;
    private EmailOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        cipher = new EmailPayloadCipher("unit-test-key");
        publisher = new EmailOutboxPublisher(repository, objectMapper, cipher);
    }

    @Test
    void enqueue_storesPendingEncryptedEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        SendMailRequest request = SendMailRequest.of(
                List.of("donor@example.com"),
                "noreply@sondhan.test",
                "Verification code",
                "<p>Your code is 123456</p>");

        String eventId = publisher.enqueue(request, EmailNotificationType.OTP, userId);

        ArgumentCaptor<EmailOutboxMessage> captor = ArgumentCaptor.forClass(EmailOutboxMessage.class);
        verify(repository).save(captor.capture());
        EmailOutboxMessage row = captor.getValue();

        assertNotNull(eventId);
        assertEquals(eventId, row.getEventId());
        assertEquals(userId.toString(), row.getAggregateId());
        assertEquals("EMAIL_NOTIFICATION", row.getEventType());
        assertEquals("email.notification", row.getTopicName());
        assertEquals(EmailOutboxStatus.PENDING, row.getStatus());
        assertEquals(0, row.getAttempts());
        assertNotNull(row.getNextAttemptAt());

        EmailNotificationEvent envelope =
                objectMapper.readValue(row.getPayload(), EmailNotificationEvent.class);
        assertEquals(EmailNotificationType.OTP, envelope.getNotificationType());
        assertNotNull(envelope.getPayload());
        assertNotEquals("123456", envelope.getPayload());
        assertNotEquals("donor@example.com", envelope.getPayload());

        SendMailRequest decoded = objectMapper.readValue(
                cipher.decrypt(envelope.getPayload()), SendMailRequest.class);
        assertEquals(List.of("donor@example.com"), decoded.getMailTo());
        assertEquals("Verification code", decoded.getSubject());
        assertEquals("<p>Your code is 123456</p>", decoded.getBody());
    }

    @Test
    void enqueue_usesUnknownAggregateWhenUserIsMissing() {
        SendMailRequest request = SendMailRequest.of(
                List.of("donor@example.com"), "noreply@sondhan.test", "subject", "body");

        publisher.enqueue(request, EmailNotificationType.GENERAL, null);

        ArgumentCaptor<EmailOutboxMessage> captor = ArgumentCaptor.forClass(EmailOutboxMessage.class);
        verify(repository).save(captor.capture());
        assertEquals("unknown", captor.getValue().getAggregateId());
    }

    @Test
    void enqueue_rejectsAttachments() {
        SendMailRequest request = SendMailRequest.of(
                List.of("donor@example.com"), "noreply@sondhan.test", "subject", "body");
        request.setAttachments(List.of(new MailAttachment("report.pdf", null)));

        assertThrows(IllegalArgumentException.class,
                () -> publisher.enqueue(request, EmailNotificationType.GENERAL, UUID.randomUUID()));
        verify(repository, never()).save(any(EmailOutboxMessage.class));
    }

    @Test
    void enqueue_rejectsEmptyRecipients() {
        SendMailRequest request = SendMailRequest.of(
                List.of(), "noreply@sondhan.test", "subject", "body");

        assertThrows(IllegalArgumentException.class,
                () -> publisher.enqueue(request, EmailNotificationType.GENERAL, UUID.randomUUID()));
        verify(repository, never()).save(any(EmailOutboxMessage.class));
    }
}
