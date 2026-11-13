package com.bd.blooddonorfinder.service.otp;

import com.bd.blooddonorfinder.exception.AuthException;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.OtpPurpose;
import com.bd.blooddonorfinder.service.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpIssuanceServiceTest {

    @Mock
    private OtpService otpService;
    @Mock
    private OtpDeliveryService otpDeliveryService;

    private OtpIssuanceService issuanceService;
    private User user;

    @BeforeEach
    void setUp() {
        issuanceService = new OtpIssuanceService(otpService, otpDeliveryService);
        user = new User();
        user.setId(UUID.randomUUID());
    }

    @Test
    void issue_generatesThenDeliversOtp() {
        when(otpService.generate(user, OtpPurpose.REGISTER))
                .thenReturn(new OtpService.GeneratedOtp("123456", 180));

        assertEquals(180, issuanceService.issue(user, OtpPurpose.REGISTER));

        verify(otpService).generate(user, OtpPurpose.REGISTER);
        verify(otpDeliveryService).deliver(user, "123456", OtpPurpose.REGISTER);
    }

    @Test
    void issue_propagatesDeliveryFailures() {
        when(otpService.generate(user, OtpPurpose.REGISTER))
                .thenReturn(new OtpService.GeneratedOtp("123456", 180));
        doThrow(new AuthException(ErrorCode.OTP_SEND_FAILED, "delivery failed"))
                .when(otpDeliveryService).deliver(user, "123456", OtpPurpose.REGISTER);

        AuthException exception = assertThrows(AuthException.class,
                () -> issuanceService.issue(user, OtpPurpose.REGISTER));

        assertEquals(ErrorCode.OTP_SEND_FAILED, exception.getErrorCode());
    }
}
