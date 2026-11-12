package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.exception.InvalidOtpException;
import com.bd.blooddonorfinder.exception.OtpAlreadyUsedException;
import com.bd.blooddonorfinder.exception.OtpExpiredException;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.OtpCode;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.OtpPurpose;
import com.bd.blooddonorfinder.repository.OtpCodeRepository;
import com.bd.blooddonorfinder.service.auth.PiiService;
import com.bd.blooddonorfinder.service.otp.OtpFailureTracker;
import com.bd.blooddonorfinder.utils.HashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    private static final String HASH_KEY = "test-otp-hash-key";
    private static final String RAW_OTP = "123456";

    @Mock
    private OtpCodeRepository otpCodeRepository;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private PiiService piiService;
    @Mock
    private OtpFailureTracker otpFailureTracker;

    private OtpService otpService;
    private User user;

    @BeforeEach
    void setUp() {
        otpService = new OtpService(
                otpCodeRepository, redisTemplate, piiService, otpFailureTracker);
        ReflectionTestUtils.setField(otpService, "otpHashKey", HASH_KEY);

        user = new User();
        user.setId(UUID.randomUUID());
    }

    @Test
    void verifyOtp_shouldRejectWrongOtpAndRecordFailure() {
        OtpCode activeOtp = activeOtp("different-hash");
        when(otpCodeRepository.findActiveOtp(eq(user), eq(OtpPurpose.REGISTER), any(Instant.class)))
                .thenReturn(Optional.of(activeOtp));

        InvalidOtpException exception = assertThrows(InvalidOtpException.class,
                () -> otpService.verifyOtp(user, RAW_OTP, OtpPurpose.REGISTER));

        assertEquals(ErrorCode.INVALID_OTP, exception.getErrorCode());
        verify(otpFailureTracker).recordFailure(
                eq(user.getId()), eq(5), any(Instant.class));
    }

    @Test
    void verifyOtp_shouldReportAlreadyUsedOtpWhenNoActiveOtpExists() {
        OtpCode usedOtp = activeOtp("used-hash");
        usedOtp.markUsed();
        when(otpCodeRepository.findActiveOtp(eq(user), eq(OtpPurpose.REGISTER), any(Instant.class)))
                .thenReturn(Optional.empty());
        when(otpCodeRepository.findFirstByUserAndPurposeOrderByCreatedAtDesc(
                user, OtpPurpose.REGISTER)).thenReturn(Optional.of(usedOtp));

        OtpAlreadyUsedException exception = assertThrows(OtpAlreadyUsedException.class,
                () -> otpService.verifyOtp(user, RAW_OTP, OtpPurpose.REGISTER));

        assertEquals(ErrorCode.OTP_ALREADY_USED, exception.getErrorCode());
        verifyNoInteractions(otpFailureTracker);
    }

    @Test
    void verifyOtp_shouldReportExpiredOtpWhenNoOtpWasIssued() {
        when(otpCodeRepository.findActiveOtp(eq(user), eq(OtpPurpose.REGISTER), any(Instant.class)))
                .thenReturn(Optional.empty());
        when(otpCodeRepository.findFirstByUserAndPurposeOrderByCreatedAtDesc(
                user, OtpPurpose.REGISTER)).thenReturn(Optional.empty());

        OtpExpiredException exception = assertThrows(OtpExpiredException.class,
                () -> otpService.verifyOtp(user, RAW_OTP, OtpPurpose.REGISTER));

        assertEquals(ErrorCode.OTP_EXPIRED, exception.getErrorCode());
    }

    @Test
    void verifyOtp_shouldMarkMatchingOtpAsUsed() {
        OtpCode activeOtp = activeOtp(hash(RAW_OTP));
        when(otpCodeRepository.findActiveOtp(eq(user), eq(OtpPurpose.REGISTER), any(Instant.class)))
                .thenReturn(Optional.of(activeOtp));
        when(otpCodeRepository.markAsUsed(any(), any(Instant.class))).thenReturn(1);

        assertDoesNotThrow(() -> otpService.verifyOtp(user, RAW_OTP, OtpPurpose.REGISTER));

        verify(otpCodeRepository).markAsUsed(eq(activeOtp.getId()), any(Instant.class));
        verifyNoInteractions(otpFailureTracker);
    }

    @Test
    void verifyOtp_shouldReportAlreadyUsedWhenConcurrentRequestWon() {
        OtpCode activeOtp = activeOtp(hash(RAW_OTP));
        when(otpCodeRepository.findActiveOtp(eq(user), eq(OtpPurpose.REGISTER), any(Instant.class)))
                .thenReturn(Optional.of(activeOtp));
        when(otpCodeRepository.markAsUsed(any(), any(Instant.class))).thenReturn(0);

        OtpAlreadyUsedException exception = assertThrows(OtpAlreadyUsedException.class,
                () -> otpService.verifyOtp(user, RAW_OTP, OtpPurpose.REGISTER));

        assertEquals(ErrorCode.OTP_ALREADY_USED, exception.getErrorCode());
    }

    private OtpCode activeOtp(String codeHash) {
        return OtpCode.of(user, codeHash, OtpPurpose.REGISTER,
                Instant.now().plusSeconds(180));
    }

    private String hash(String otp) {
        return HashUtils.hmacSha256Hex(
                HASH_KEY, user.getId() + ":" + OtpPurpose.REGISTER.name() + ":" + otp);
    }
}
