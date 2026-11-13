package com.bd.blooddonorfinder.kafka.consumer;

import com.bd.blooddonorfinder.exception.EmailDeliveryException;
import com.bd.blooddonorfinder.exception.EmailPayloadException;
import com.bd.blooddonorfinder.kafka.idempotency.ProcessedEventStore;
import com.bd.blooddonorfinder.kafka.model.events.EmailNotificationEvent;
import com.bd.blooddonorfinder.kafka.outbox.EmailOutboxDeliveryTracker;
import com.bd.blooddonorfinder.model.enums.EmailNotificationType;
import com.bd.blooddonorfinder.payload.request.SendMailRequest;
import com.bd.blooddonorfinder.service.MailService;
import com.bd.blooddonorfinder.utils.EmailPayloadCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailNotificationConsumerTest {

    @Mock
    private MailService mailService;
    @Mock
    private ProcessedEventStore processedEventStore;
    @Mock
    private EmailOutboxDeliveryTracker deliveryTracker;
    @Mock
    private Acknowledgment acknowledgment;

    private ObjectMapper objectMapper;
    private EmailPayloadCipher cipher;
    private EmailNotificationConsumer consumer;
    private UUID userId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        cipher = new EmailPayloadCipher("unit-test-key");
        consumer = new EmailNotificationConsumer(
                mailService, processedEventStore, cipher, objectMapper, deliveryTracker);
        userId = UUID.randomUUID();
    }

    @Test
    void deliversMailAndAcknowledgesOnce() throws Exception {
        EmailNotificationEvent event = event("donor@example.com", "Your code is 123456");
        when(mailService.sendMail(any(SendMailRequest.class))).thenReturn(true);

        consumer.onEmailNotification(event, acknowledgment);

        ArgumentCaptor<SendMailRequest> requestCaptor = ArgumentCaptor.forClass(SendMailRequest.class);
        verify(mailService).sendMail(requestCaptor.capture());
        assertEquals(List.of("donor@example.com"), requestCaptor.getValue().getMailTo());
        assertEquals("Your code is 123456", requestCaptor.getValue().getBody());

        verify(deliveryTracker).markDelivered(event.getEventId());
        verify(processedEventStore).markProcessedIfAbsent(
                event.getEventId(), "email.notification", userId.toString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void skipsAlreadyProcessedEventWithoutSendingAgain() throws Exception {
        EmailNotificationEvent event = event("donor@example.com", "body");
        when(processedEventStore.isProcessed(event.getEventId())).thenReturn(true);

        consumer.onEmailNotification(event, acknowledgment);

        verifyNoInteractions(mailService, deliveryTracker);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void transportRejectionIsRetryableAndDoesNotAcknowledge() throws Exception {
        EmailNotificationEvent event = event("donor@example.com", "body");
        when(mailService.sendMail(any(SendMailRequest.class))).thenReturn(false);

        assertThrows(EmailDeliveryException.class,
                () -> consumer.onEmailNotification(event, acknowledgment));

        verify(deliveryTracker, never()).markDelivered(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void transportExceptionIsWrappedAsRetryableFailure() throws Exception {
        EmailNotificationEvent event = event("donor@example.com", "body");
        when(mailService.sendMail(any(SendMailRequest.class)))
                .thenThrow(new IllegalStateException("smtp unavailable"));

        assertThrows(EmailDeliveryException.class,
                () -> consumer.onEmailNotification(event, acknowledgment));

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void corruptPayloadIsAPermanentFailure() {
        EmailNotificationEvent event = EmailNotificationEvent.of(
                EmailNotificationType.OTP, userId.toString(), "not-a-valid-ciphertext");

        assertThrows(EmailPayloadException.class,
                () -> consumer.onEmailNotification(event, acknowledgment));

        verifyNoInteractions(mailService, deliveryTracker);
        verify(acknowledgment, never()).acknowledge();
    }

    private EmailNotificationEvent event(String recipient, String body) throws Exception {
        SendMailRequest request = SendMailRequest.of(
                List.of(recipient), "noreply@sondhan.test", "Sondhan verification", body);
        String encrypted = cipher.encrypt(objectMapper.writeValueAsString(request));
        return EmailNotificationEvent.of(
                EmailNotificationType.OTP, userId.toString(), encrypted);
    }
}
