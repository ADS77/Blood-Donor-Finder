package com.bd.blooddonorfinder.exception;

import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.payload.request.LoginRequest;
import com.bd.blooddonorfinder.payload.request.VerifyOtpRequest;
import com.bd.blooddonorfinder.payload.response.ApiErrorResponse;
import com.bd.blooddonorfinder.service.auth.AuthFailureRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private static final String IP = "127.0.0.1";

    @Mock
    private AuthFailureRateLimiter rateLimiter;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(rateLimiter);
    }

    @Test
    void invalidOtp_shouldReturnSpecific400Response() {
        MockHttpServletRequest request = authRequest("/sondhan/api/auth/verify-otp");

        ResponseEntity<ApiErrorResponse> response = handler.handleAuthException(
                new InvalidOtpException(ErrorCode.INVALID_OTP, "OTP is incorrect"), request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("INVALID_OTP", response.getBody().getError());
        assertEquals("OTP is incorrect", response.getBody().getMessage());
    }

    @Test
    void expiredOtp_shouldReturnSpecific400Response() {
        MockHttpServletRequest request = authRequest("/sondhan/api/auth/verify-otp");

        ResponseEntity<ApiErrorResponse> response = handler.handleAuthException(
                new OtpExpiredException(ErrorCode.OTP_EXPIRED, "No active OTP found"),
                request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("OTP_EXPIRED", response.getBody().getError());
    }

    @Test
    void alreadyUsedOtp_shouldReturn409Response() {
        MockHttpServletRequest request = authRequest("/sondhan/api/auth/verify-otp");

        ResponseEntity<ApiErrorResponse> response = handler.handleAuthException(
                new OtpAlreadyUsedException(ErrorCode.OTP_ALREADY_USED, "OTP has already been used"),
                request);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("OTP_ALREADY_USED", response.getBody().getError());
    }

    @Test
    void alreadyVerifiedUser_shouldReturn409Response() {
        MockHttpServletRequest request = authRequest("/sondhan/api/auth/verify-otp");

        ResponseEntity<ApiErrorResponse> response = handler.handleAuthException(
                new AuthException(ErrorCode.ALREADY_VERIFIED,
                        "User is already registered and verified"),
                request);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("ALREADY_VERIFIED", response.getBody().getError());
    }

    @Test
    void lockedAccount_shouldReturn403Response() {
        MockHttpServletRequest request = authRequest("/sondhan/api/auth/verify-otp");

        ResponseEntity<ApiErrorResponse> response = handler.handleAuthException(
                new AccountLockedException(ErrorCode.ACCOUNT_LOCKED, "User account is locked"),
                request);

        assertEquals(403, response.getStatusCode().value());
        assertEquals("ACCOUNT_LOCKED", response.getBody().getError());
    }

    @Test
    void unsupportedOtpPurpose_shouldReturn400Response() {
        MockHttpServletRequest request = authRequest("/sondhan/api/auth/verify-otp");

        ResponseEntity<ApiErrorResponse> response = handler.handleAuthException(
                new InvalidOtpPurposeException(ErrorCode.UNSUPPORTED_OTP_PURPOSE,
                        "Unsupported OTP purpose: PASSWORD_RESET"),
                request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("UNSUPPORTED_OTP_PURPOSE", response.getBody().getError());
    }

    @Test
    void otpDeliveryUnavailable_shouldReturn400Response() {
        MockHttpServletRequest request = authRequest("/sondhan/api/auth/register");

        ResponseEntity<ApiErrorResponse> response = handler.handleAuthException(
                new AuthException(ErrorCode.OTP_DELIVERY_UNAVAILABLE,
                        "No delivery channel is available for this user"),
                request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("OTP_DELIVERY_UNAVAILABLE", response.getBody().getError());
    }

    @Test
    void otpSendFailed_shouldReturn502Response() {
        MockHttpServletRequest request = authRequest("/sondhan/api/auth/register");

        ResponseEntity<ApiErrorResponse> response = handler.handleAuthException(
                new AuthException(ErrorCode.OTP_SEND_FAILED,
                        "Failed to send OTP. Please try again later."),
                request);

        assertEquals(502, response.getStatusCode().value());
        assertEquals("OTP_SEND_FAILED", response.getBody().getError());
    }

    @Test
    void validationFailureOnVerifyOtp_shouldCountAgainstIpUserAndPurpose() throws Exception {
        UUID userId = UUID.randomUUID();
        VerifyOtpRequest target = new VerifyOtpRequest();
        target.setUserId(userId);
        target.setOtp("not-six-digits");
        target.setPurpose("LOGIN");

        MethodArgumentNotValidException exception = validationException(target);
        MockHttpServletRequest request = authRequest("/sondhan/api/auth/verify-otp");

        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(exception, request);

        assertEquals(400, response.getStatusCode().value());
        verify(rateLimiter).checkAllowed("verify-otp:LOGIN", IP, userId.toString());
        verify(rateLimiter).recordFailure("verify-otp:LOGIN", IP, userId.toString());
    }

    @Test
    void validationFailureOnVerifyOtp_shouldReturn429WhenLimitExceeded() throws Exception {
        UUID userId = UUID.randomUUID();
        VerifyOtpRequest target = new VerifyOtpRequest();
        target.setUserId(userId);
        target.setPurpose("LOGIN");

        MethodArgumentNotValidException exception = validationException(target);
        MockHttpServletRequest request = authRequest("/sondhan/api/auth/verify-otp");
        doThrow(new RateLimitExceededException("limited", 42L))
                .when(rateLimiter).checkAllowed("verify-otp:LOGIN", IP, userId.toString());

        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(exception, request);

        assertEquals(429, response.getStatusCode().value());
        assertEquals("42", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        verify(rateLimiter, never()).recordFailure(
                "verify-otp:LOGIN", IP, userId.toString());
    }

    @Test
    void validationFailureOnLogin_shouldCountAgainstUnknownSubject() throws Exception {
        LoginRequest target = new LoginRequest();
        target.setIdentifier("not-an-identifier");
        target.setPassword("weak");

        MethodArgumentNotValidException exception = validationException(target);
        MockHttpServletRequest request = authRequest("/sondhan/api/auth/login");

        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(exception, request);

        assertEquals(400, response.getStatusCode().value());
        verify(rateLimiter).checkAllowed("login", IP, "unknown");
        verify(rateLimiter).recordFailure("login", IP, "unknown");
    }

    @Test
    void malformedVerifyOtpRequest_shouldCountAgainstInvalidPurpose() {
        MockHttpServletRequest request = authRequest("/sondhan/api/auth/verify-otp");

        ResponseEntity<ApiErrorResponse> response = handler.handleMalformedRequest(
                new org.springframework.http.converter.HttpMessageNotReadableException("bad json"),
                request);

        assertEquals(400, response.getStatusCode().value());
        verify(rateLimiter).checkAllowed("verify-otp:INVALID", IP, "unknown");
        verify(rateLimiter).recordFailure("verify-otp:INVALID", IP, "unknown");
    }

    private MethodArgumentNotValidException validationException(Object target) throws Exception {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(target, "request");
        bindingResult.reject("invalid", "invalid request");
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("endpoint", Object.class), 0);
        return new MethodArgumentNotValidException(parameter, bindingResult);
    }

    @SuppressWarnings("unused")
    private void endpoint(Object body) {
    }

    private MockHttpServletRequest authRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(IP);
        return request;
    }
}
