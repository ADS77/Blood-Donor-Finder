package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.kafka.outbox.EmailOutboxPublisher;
import com.bd.blooddonorfinder.model.common.GeoLocation;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.EmailNotificationType;
import com.bd.blooddonorfinder.payload.request.DonorSearchRequest;
import com.bd.blooddonorfinder.payload.request.SendMailRequest;
import com.bd.blooddonorfinder.service.auth.PiiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationManagerImplTest {

    @Mock
    private EmailOutboxPublisher emailOutboxPublisher;
    @Mock
    private PiiService piiService;

    private NotificationManagerImpl notificationManager;

    @BeforeEach
    void setUp() {
        notificationManager = new NotificationManagerImpl(emailOutboxPublisher, piiService);
        ReflectionTestUtils.setField(notificationManager, "orgName", "Team Sondhan");
        ReflectionTestUtils.setField(notificationManager, "orgEmail", "noreply@sondhan.test");
    }

    @Test
    void notifyByMail_queuesOtpWithoutCallingSmtp() {
        User user = user("John");
        when(emailOutboxPublisher.enqueue(any(SendMailRequest.class),
                eq(EmailNotificationType.OTP), eq(user.getId()))).thenReturn("event-1");

        boolean queued = notificationManager.notifyByMail(
                user, "donor@example.com", "123456", "REGISTER");

        assertTrue(queued);
        ArgumentCaptor<SendMailRequest> captor = ArgumentCaptor.forClass(SendMailRequest.class);
        verify(emailOutboxPublisher).enqueue(
                captor.capture(), eq(EmailNotificationType.OTP), eq(user.getId()));

        SendMailRequest request = captor.getValue();
        assertEquals(List.of("donor@example.com"), request.getMailTo());
        assertEquals("noreply@sondhan.test", request.getMailFrom());
        assertEquals("Sondhan verification code for REGISTER", request.getSubject());
        assertTrue(request.getBody().contains("123456"));
        assertTrue(request.isHtmlContent());
    }

    @Test
    void notifyByMail_queuesOneEventPerDonorWithResolvedEmail() {
        User donor = user("Jane");
        when(piiService.decryptEmail(donor)).thenReturn("jane@example.com");

        notificationManager.notifyByMail(List.of(donor), donorSearchRequest());

        ArgumentCaptor<SendMailRequest> captor = ArgumentCaptor.forClass(SendMailRequest.class);
        verify(emailOutboxPublisher).enqueue(
                captor.capture(), eq(EmailNotificationType.DONOR_ALERT), eq(donor.getId()));
        assertEquals(List.of("jane@example.com"), captor.getValue().getMailTo());
    }

    @Test
    void notifyByMail_skipsDonorWithInvalidEmail() {
        User donor = user("Jane");
        when(piiService.decryptEmail(donor)).thenReturn("not-an-email");

        notificationManager.notifyByMail(List.of(donor), donorSearchRequest());

        verifyNoInteractions(emailOutboxPublisher);
    }

    @Test
    void notifyByMail_skipsDonorWithoutPiiToken() {
        User donor = user("Jane");
        when(piiService.decryptEmail(donor))
                .thenThrow(new IllegalStateException("User has no email token"));

        notificationManager.notifyByMail(List.of(donor), donorSearchRequest());

        verify(emailOutboxPublisher, never()).enqueue(any(), any(), any());
    }

    @Test
    void notifyByMail_ignoresEmptyDonorList() {
        notificationManager.notifyByMail(List.of(), donorSearchRequest());

        verifyNoInteractions(emailOutboxPublisher, piiService);
    }

    private User user(String firstName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName(firstName);
        return user;
    }

    private DonorSearchRequest donorSearchRequest() {
        DonorSearchRequest request = new DonorSearchRequest();
        GeoLocation location = new GeoLocation();
        location.setCity("Dhaka");
        request.setGeoLocation(location);
        request.setReceiverPhone("+8801700000000");
        return request;
    }
}
