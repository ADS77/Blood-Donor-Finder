package com.bd.blooddonorfinder.service.otp;

import com.bd.blooddonorfinder.exception.AuthException;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.OtpPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpDeliveryServiceTest {

    @Mock
    private OtpChannel emailChannel;
    @Mock
    private OtpChannel smsChannel;

    private OtpDeliveryService deliveryService;
    private User user;

    @BeforeEach
    void setUp() {
        deliveryService = new OtpDeliveryService(List.of(emailChannel, smsChannel));
        user = new User();
        user.setId(UUID.randomUUID());
    }

    @Test
    void deliver_usesFirstChannelThatCanReachUser() {
        when(emailChannel.resolveRecipient(user)).thenReturn(Optional.of("donor@example.com"));
        when(emailChannel.type()).thenReturn(OtpChannelType.EMAIL);
        when(emailChannel.send(any())).thenReturn(true);

        deliveryService.deliver(user, "123456", OtpPurpose.REGISTER);

        ArgumentCaptor<OtpMessage> message = ArgumentCaptor.forClass(OtpMessage.class);
        verify(emailChannel).send(message.capture());
        assertEquals("donor@example.com", message.getValue().recipient());
        assertEquals("123456", message.getValue().otp());
        assertEquals(OtpPurpose.REGISTER, message.getValue().purpose());
        verify(smsChannel, never()).send(any());
    }

    @Test
    void deliver_fallsBackToNextChannelWhenFirstCannotReachUser() {
        when(emailChannel.resolveRecipient(user)).thenReturn(Optional.empty());
        when(smsChannel.resolveRecipient(user)).thenReturn(Optional.of("+8801700000000"));
        when(smsChannel.type()).thenReturn(OtpChannelType.SMS);
        when(smsChannel.send(any())).thenReturn(true);

        deliveryService.deliver(user, "123456", OtpPurpose.LOGIN);

        ArgumentCaptor<OtpMessage> message = ArgumentCaptor.forClass(OtpMessage.class);
        verify(smsChannel).send(message.capture());
        assertEquals("+8801700000000", message.getValue().recipient());
        verify(emailChannel, never()).send(any());
    }

    @Test
    void deliver_fallsBackWhenFirstChannelRejectsMessage() {
        when(emailChannel.resolveRecipient(user)).thenReturn(Optional.of("donor@example.com"));
        when(emailChannel.type()).thenReturn(OtpChannelType.EMAIL);
        when(emailChannel.send(any())).thenReturn(false);
        when(smsChannel.resolveRecipient(user)).thenReturn(Optional.of("+8801700000000"));
        when(smsChannel.type()).thenReturn(OtpChannelType.SMS);
        when(smsChannel.send(any())).thenReturn(true);

        deliveryService.deliver(user, "123456", OtpPurpose.LOGIN);

        verify(emailChannel).send(any());
        verify(smsChannel).send(any());
    }

    @Test
    void deliver_fallsBackWhenFirstChannelProviderFails() {
        when(emailChannel.resolveRecipient(user)).thenReturn(Optional.of("donor@example.com"));
        when(emailChannel.type()).thenReturn(OtpChannelType.EMAIL);
        when(emailChannel.send(any())).thenThrow(
                new AuthException(ErrorCode.OTP_SEND_FAILED, "mail provider unavailable"));
        when(smsChannel.resolveRecipient(user)).thenReturn(Optional.of("+8801700000000"));
        when(smsChannel.type()).thenReturn(OtpChannelType.SMS);
        when(smsChannel.send(any())).thenReturn(true);

        deliveryService.deliver(user, "123456", OtpPurpose.LOGIN);

        verify(emailChannel).send(any());
        verify(smsChannel).send(any());
    }

    @Test
    void deliver_throwsChannelAgnosticErrorWhenNoChannelCanReachUser() {
        when(emailChannel.resolveRecipient(user)).thenReturn(Optional.empty());
        when(smsChannel.resolveRecipient(user)).thenReturn(Optional.empty());

        AuthException exception = assertThrows(AuthException.class,
                () -> deliveryService.deliver(user, "123456", OtpPurpose.REGISTER));

        assertEquals(ErrorCode.OTP_DELIVERY_UNAVAILABLE, exception.getErrorCode());
    }

    @Test
    void deliver_throwsSendFailedWhenEveryReachableChannelFails() {
        when(emailChannel.resolveRecipient(user)).thenReturn(Optional.of("donor@example.com"));
        when(emailChannel.type()).thenReturn(OtpChannelType.EMAIL);
        when(emailChannel.send(any())).thenReturn(false);
        when(smsChannel.resolveRecipient(user)).thenReturn(Optional.of("+8801700000000"));
        when(smsChannel.type()).thenReturn(OtpChannelType.SMS);
        when(smsChannel.send(any())).thenThrow(
                new AuthException(ErrorCode.OTP_SEND_FAILED, "sms provider unavailable"));

        AuthException exception = assertThrows(AuthException.class,
                () -> deliveryService.deliver(user, "123456", OtpPurpose.LOGIN));

        assertEquals(ErrorCode.OTP_SEND_FAILED, exception.getErrorCode());
    }

    @Test
    void deliver_propagatesErrorsThatAreNotProviderFailures() {
        when(emailChannel.resolveRecipient(user)).thenReturn(Optional.of("donor@example.com"));
        when(emailChannel.send(any())).thenThrow(
                new AuthException(ErrorCode.INVALID_REQUEST, "malformed message"));

        AuthException exception = assertThrows(AuthException.class,
                () -> deliveryService.deliver(user, "123456", OtpPurpose.LOGIN));

        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
        verify(smsChannel, never()).send(any());
    }
}
