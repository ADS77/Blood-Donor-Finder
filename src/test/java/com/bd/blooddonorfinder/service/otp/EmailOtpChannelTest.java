package com.bd.blooddonorfinder.service.otp;

import com.bd.blooddonorfinder.exception.AuthException;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.OtpPurpose;
import com.bd.blooddonorfinder.service.NotificationManager;
import com.bd.blooddonorfinder.service.auth.PiiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailOtpChannelTest {

    @Mock
    private PiiService piiService;
    @Mock
    private NotificationManager notificationManager;

    private EmailOtpChannel channel;

    @BeforeEach
    void setUp() {
        channel = new EmailOtpChannel(piiService, notificationManager);
    }

    @Test
    void resolveRecipient_returnsEmailWhenValid() {
        User user = user();
        when(piiService.decryptEmail(user)).thenReturn("donor@example.com");

        assertEquals(Optional.of("donor@example.com"), channel.resolveRecipient(user));
    }

    @Test
    void resolveRecipient_returnsEmptyWhenEmailIsInvalid() {
        User user = user();
        when(piiService.decryptEmail(user)).thenReturn("not-an-email");

        assertTrue(channel.resolveRecipient(user).isEmpty());
    }

    @Test
    void send_delegatesToNotificationManager() {
        User user = user();
        OtpMessage message = new OtpMessage(user, "donor@example.com", "123456", OtpPurpose.REGISTER);
        when(notificationManager.notifyByMail(user, "donor@example.com", "123456", "REGISTER"))
                .thenReturn(true);

        assertTrue(channel.send(message));
    }

    @Test
    void send_returnsFalseWhenProviderRejectsMessage() {
        User user = user();
        OtpMessage message = new OtpMessage(user, "donor@example.com", "123456", OtpPurpose.REGISTER);
        when(notificationManager.notifyByMail(user, "donor@example.com", "123456", "REGISTER"))
                .thenReturn(false);

        assertFalse(channel.send(message));
    }

    @Test
    void send_wrapsProviderFailureWithoutLeakingDetails() {
        User user = user();
        OtpMessage message = new OtpMessage(user, "donor@example.com", "123456", OtpPurpose.REGISTER);
        when(notificationManager.notifyByMail(user, "donor@example.com", "123456", "REGISTER"))
                .thenThrow(new IllegalStateException("smtp.internal:25 rejected"));

        AuthException exception = assertThrows(AuthException.class, () -> channel.send(message));

        assertEquals(ErrorCode.OTP_SEND_FAILED, exception.getErrorCode());
        assertEquals("Failed to send OTP. Please try again later.", exception.getMessage());
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName("John");
        return user;
    }
}
