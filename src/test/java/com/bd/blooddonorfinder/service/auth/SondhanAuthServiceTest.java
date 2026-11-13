package com.bd.blooddonorfinder.service.auth;

import com.bd.blooddonorfinder.exception.AccountLockedException;
import com.bd.blooddonorfinder.exception.AuthException;
import com.bd.blooddonorfinder.exception.InvalidOtpException;
import com.bd.blooddonorfinder.exception.InvalidOtpPurposeException;
import com.bd.blooddonorfinder.exception.OtpAlreadyUsedException;
import com.bd.blooddonorfinder.exception.OtpExpiredException;
import com.bd.blooddonorfinder.exception.RateLimitExceededException;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.AuditEvent;
import com.bd.blooddonorfinder.model.enums.OtpPurpose;
import com.bd.blooddonorfinder.model.enums.TokenType;
import com.bd.blooddonorfinder.payload.enums.LoginIdentifierType;
import com.bd.blooddonorfinder.payload.request.LoginRequest;
import com.bd.blooddonorfinder.payload.request.ResendOtpRequest;
import com.bd.blooddonorfinder.payload.request.VerifyOtpRequest;
import com.bd.blooddonorfinder.payload.response.TokenResponse;
import com.bd.blooddonorfinder.security.CustomUserDetailsService;
import com.bd.blooddonorfinder.security.UserPrincipal;
import com.bd.blooddonorfinder.security.context.SondhanClientContext;
import com.bd.blooddonorfinder.security.jwt.JwtTokenProvider;
import com.bd.blooddonorfinder.service.OtpService;
import com.bd.blooddonorfinder.service.UserService;
import com.bd.blooddonorfinder.service.audit.AuditService;
import com.bd.blooddonorfinder.service.otp.OtpIssuanceService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SondhanAuthServiceTest {

    private static final String RAW_PASSWORD = "Password@123";
    private static final String ENCODED_PASSWORD = "encoded-password";
    private static final String IP = "127.0.0.1";
    private static final String USER_AGENT = "junit";

    @Mock
    private UserService userService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private CustomUserDetailsService userDetailsService;
    @Mock
    private OtpService otpService;
    @Mock
    private OtpIssuanceService otpIssuanceService;
    @Mock
    private AuditService auditService;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private TokenStorageService tokenStorageService;
    @Mock
    private AuthFailureRateLimiter authFailureRateLimiter;

    private SondhanAuthService authService;
    private SondhanClientContext clientContext;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
        authService = new SondhanAuthService(
                userService,
                passwordEncoder,
                userDetailsService,
                otpService,
                otpIssuanceService,
                auditService,
                tokenProvider,
                tokenStorageService,
                authFailureRateLimiter);
        clientContext = SondhanClientContext.builder()
                .ipAddress(IP)
                .userAgent(USER_AGENT)
                .build();
    }

    @Test
    void login_shouldIssueTokenPairForValidCredentials() throws Exception {
        User user = activeUser();
        UserPrincipal principal = principalFor(user);
        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken("access")
                .refreshToken("refresh")
                .build();

        when(userService.findByIdentifier("john@example.com", LoginIdentifierType.EMAIL)).thenReturn(user);
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        when(userDetailsService.buildPrincipal(user)).thenReturn(principal);
        when(tokenProvider.getTokenPair(principal)).thenReturn(tokenResponse);

        var response = authService.login(loginRequest("john@example.com"), clientContext);

        assertNotNull(response.getSuccess());
        assertEquals(tokenResponse, response.getSuccess().getData().getTokenResponse());
        verify(auditService).log(AuditEvent.LOGIN, user.getId(), IP, USER_AGENT, "{\"success\":true}");
    }

    @Test
    void login_shouldRejectUnknownIdentifierWithoutRevealingIt() {
        when(userService.findByIdentifier("missing@example.com", LoginIdentifierType.EMAIL))
                .thenThrow(new AuthException(ErrorCode.USER_NOT_FOUND, "No account found"));

        assertThrows(BadCredentialsException.class,
                () -> authService.login(loginRequest("missing@example.com"), clientContext));
        verify(authFailureRateLimiter).checkAllowed("login", IP, "unknown");
        verify(authFailureRateLimiter).recordFailure("login", IP, "unknown");
    }

    @Test
    void login_shouldCountFailuresBeforeSubjectResolution() {
        assertThrows(BadCredentialsException.class,
                () -> authService.login(loginRequest(" "), clientContext));

        verify(authFailureRateLimiter).checkAllowed("login", IP, "unknown");
        verify(authFailureRateLimiter).recordFailure("login", IP, "unknown");
    }

    @Test
    void login_shouldRejectWrongPassword() {
        User user = activeUser();
        when(userService.findByIdentifier("john@example.com", LoginIdentifierType.EMAIL)).thenReturn(user);
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> authService.login(loginRequest("john@example.com"), clientContext));
        verify(authFailureRateLimiter).checkAllowed("login", IP, "user:" + user.getId());
        verify(authFailureRateLimiter).recordFailure("login", IP, "user:" + user.getId());
        verify(auditService).log(AuditEvent.LOGIN, user.getId(), IP, USER_AGENT,
                "{\"success\":false,\"reason\":\"INVALID_CREDENTIALS\"}");
    }

    @Test
    void login_shouldRejectLockedAccount() {
        User user = activeUser();
        user.setLockedUntil(Instant.now().plusSeconds(300));

        when(userService.findByIdentifier("john@example.com", LoginIdentifierType.EMAIL)).thenReturn(user);
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

        assertThrows(AccountLockedException.class,
                () -> authService.login(loginRequest("john@example.com"), clientContext));
    }

    @Test
    void verifyOtp_shouldScopeFailuresByIpUserAndPurpose() {
        User user = activeUser();
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setUserId(user.getId());
        request.setOtp("123456");
        request.setPurpose("LOGIN");

        when(userService.findById(user.getId())).thenReturn(user);
        doThrow(new InvalidOtpException(ErrorCode.INVALID_OTP, "Invalid OTP"))
                .when(otpService).verifyOtp(user, "123456", OtpPurpose.LOGIN);

        assertThrows(InvalidOtpException.class,
                () -> authService.verifyOtp(clientContext, request));

        verify(authFailureRateLimiter).checkAllowed(
                "verify-otp:LOGIN", IP, user.getId().toString());
        verify(authFailureRateLimiter).recordFailure(
                "verify-otp:LOGIN", IP, user.getId().toString());
    }

    @Test
    void verifyOtp_shouldRejectRegistrationForAlreadyVerifiedUser() {
        User user = activeUser();
        VerifyOtpRequest request = verifyOtpRequest(user.getId(), "REGISTER");

        when(userService.findById(user.getId())).thenReturn(user);

        AuthException exception = assertThrows(AuthException.class,
                () -> authService.verifyOtp(clientContext, request));

        assertEquals(ErrorCode.ALREADY_VERIFIED, exception.getErrorCode());
        verifyNoInteractions(otpService);
        verify(authFailureRateLimiter).recordFailure(
                "verify-otp:REGISTER", IP, user.getId().toString());
    }

    @Test
    void verifyOtp_shouldPropagateExpiredOtp() {
        User user = registrationUser();
        VerifyOtpRequest request = verifyOtpRequest(user.getId(), "REGISTER");

        when(userService.findById(user.getId())).thenReturn(user);
        doThrow(new OtpExpiredException(ErrorCode.OTP_EXPIRED, "No active OTP found"))
                .when(otpService).verifyOtp(user, "123456", OtpPurpose.REGISTER);

        OtpExpiredException exception = assertThrows(OtpExpiredException.class,
                () -> authService.verifyOtp(clientContext, request));

        assertEquals(ErrorCode.OTP_EXPIRED, exception.getErrorCode());
        verify(authFailureRateLimiter).recordFailure(
                "verify-otp:REGISTER", IP, user.getId().toString());
    }

    @Test
    void verifyOtp_shouldPropagateAlreadyUsedOtp() {
        User user = registrationUser();
        VerifyOtpRequest request = verifyOtpRequest(user.getId(), "REGISTER");

        when(userService.findById(user.getId())).thenReturn(user);
        doThrow(new OtpAlreadyUsedException(ErrorCode.OTP_ALREADY_USED, "OTP has already been used"))
                .when(otpService).verifyOtp(user, "123456", OtpPurpose.REGISTER);

        OtpAlreadyUsedException exception = assertThrows(OtpAlreadyUsedException.class,
                () -> authService.verifyOtp(clientContext, request));

        assertEquals(ErrorCode.OTP_ALREADY_USED, exception.getErrorCode());
        verify(authFailureRateLimiter).recordFailure(
                "verify-otp:REGISTER", IP, user.getId().toString());
    }

    @Test
    void verifyOtp_shouldRejectUnsupportedPurpose() {
        User user = registrationUser();
        VerifyOtpRequest request = verifyOtpRequest(user.getId(), "PASSWORD_RESET");

        when(userService.findById(user.getId())).thenReturn(user);

        InvalidOtpPurposeException exception = assertThrows(InvalidOtpPurposeException.class,
                () -> authService.verifyOtp(clientContext, request));

        assertEquals(ErrorCode.UNSUPPORTED_OTP_PURPOSE, exception.getErrorCode());
        verifyNoInteractions(otpService);
    }

    @Test
    void verifyOtp_shouldReturnRateLimitWithoutRecordingAnotherFailure() {
        UUID userId = UUID.randomUUID();
        VerifyOtpRequest request = verifyOtpRequest(userId, "REGISTER");
        doThrow(new RateLimitExceededException("limited", 30L))
                .when(authFailureRateLimiter)
                .checkAllowed("verify-otp:REGISTER", IP, userId.toString());

        assertThrows(RateLimitExceededException.class,
                () -> authService.verifyOtp(clientContext, request));

        verifyNoInteractions(userService);
        verify(authFailureRateLimiter, never())
                .recordFailure(anyString(), anyString(), anyString());
    }

    @Test
    void verifyOtp_shouldCompleteRegistrationAndIssueTokens() throws Exception {
        User user = registrationUser();
        VerifyOtpRequest request = verifyOtpRequest(user.getId(), "REGISTER");
        UserPrincipal principal = principalFor(user);
        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken("access")
                .refreshToken("refresh")
                .build();

        when(userService.findById(user.getId())).thenReturn(user);
        when(userService.getUserWithRolePermissions(user.getId())).thenReturn(user);
        when(userDetailsService.buildPrincipal(user)).thenReturn(principal);
        when(tokenProvider.getTokenPair(principal)).thenReturn(tokenResponse);

        var response = authService.verifyOtp(clientContext, request);

        assertNotNull(response.getSuccess());
        assertEquals(tokenResponse, response.getSuccess().getData().getTokenResponse());
        verify(otpService).verifyOtp(user, "123456", OtpPurpose.REGISTER);
        verify(userService).completeRegistrationAfterOtpVerification(user);
        verify(userService).saveUser(user);
        verify(auditService).log(
                AuditEvent.OTP_VERIFIED, user.getId(), IP, USER_AGENT);
    }

    @Test
    void resendOtp_shouldIssueNewOtpForRegistrationUser() {
        User user = registrationUser();
        ResendOtpRequest request = resendOtpRequest(user.getId(), "REGISTER");
        when(userService.findById(user.getId())).thenReturn(user);
        when(otpIssuanceService.issue(user, OtpPurpose.REGISTER)).thenReturn(180);

        var response = authService.resendOtp(clientContext, request);

        assertNotNull(response.getSuccess());
        assertEquals(user.getId(), response.getSuccess().getData().userId());
        assertEquals(180, response.getSuccess().getData().otpExpiresInSeconds());
        verify(otpIssuanceService).issue(user, OtpPurpose.REGISTER);
        verify(auditService).log(AuditEvent.OTP_SENT, user.getId(), IP, USER_AGENT,
                "{\"purpose\":\"REGISTER\",\"resend\":true}");
    }

    @Test
    void resendOtp_shouldRejectAlreadyVerifiedRegistration() {
        User user = activeUser();
        ResendOtpRequest request = resendOtpRequest(user.getId(), "REGISTER");
        when(userService.findById(user.getId())).thenReturn(user);

        AuthException exception = assertThrows(AuthException.class,
                () -> authService.resendOtp(clientContext, request));

        assertEquals(ErrorCode.ALREADY_VERIFIED, exception.getErrorCode());
        verifyNoInteractions(otpIssuanceService);
    }

    @Test
    void resendOtp_shouldRejectUnverifiedLogin() {
        User user = registrationUser();
        ResendOtpRequest request = resendOtpRequest(user.getId(), "LOGIN");
        when(userService.findById(user.getId())).thenReturn(user);

        AuthException exception = assertThrows(AuthException.class,
                () -> authService.resendOtp(clientContext, request));

        assertEquals(ErrorCode.USER_NOT_VERIFIED, exception.getErrorCode());
        verifyNoInteractions(otpIssuanceService);
    }

    @Test
    void resendOtp_shouldRejectUnsupportedPurpose() {
        User user = registrationUser();
        ResendOtpRequest request = resendOtpRequest(user.getId(), "PASSWORD_RESET");
        when(userService.findById(user.getId())).thenReturn(user);

        InvalidOtpPurposeException exception = assertThrows(InvalidOtpPurposeException.class,
                () -> authService.resendOtp(clientContext, request));

        assertEquals(ErrorCode.UNSUPPORTED_OTP_PURPOSE, exception.getErrorCode());
        verifyNoInteractions(otpIssuanceService);
    }

    @Test
    void resendOtp_shouldPropagateDeliveryFailure() {
        User user = registrationUser();
        ResendOtpRequest request = resendOtpRequest(user.getId(), "REGISTER");
        when(userService.findById(user.getId())).thenReturn(user);
        when(otpIssuanceService.issue(user, OtpPurpose.REGISTER))
                .thenThrow(new AuthException(ErrorCode.OTP_SEND_FAILED, "delivery failed"));

        AuthException exception = assertThrows(AuthException.class,
                () -> authService.resendOtp(clientContext, request));

        assertEquals(ErrorCode.OTP_SEND_FAILED, exception.getErrorCode());
    }

    @Test
    void refresh_shouldRotateWhitelistedRefreshToken() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = activeUser();
        user.setId(userId);
        UserPrincipal principal = principalFor(user);
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken("new-access")
                .refreshToken("new-refresh")
                .build();

        when(tokenProvider.parseAndValidateClaims("old-refresh")).thenReturn(claims);
        when(tokenProvider.getTokenType(claims)).thenReturn(TokenType.REFRESH_TOKEN);
        when(tokenProvider.getTokenId(claims)).thenReturn("old-jti");
        when(tokenProvider.getUserId(claims)).thenReturn(userId);
        when(tokenStorageService.consumeRefreshToken("old-jti")).thenReturn(true);
        when(userService.getUserWithRolePermissions(userId)).thenReturn(user);
        when(userDetailsService.buildPrincipal(user)).thenReturn(principal);
        when(tokenProvider.getTokenPair(principal)).thenReturn(tokenResponse);

        TokenResponse result = authService.refresh("old-refresh", clientContext);

        assertEquals(tokenResponse, result);
        verify(tokenStorageService).consumeRefreshToken("old-jti");
        verify(tokenStorageService, org.mockito.Mockito.never()).revokeAllTokensForUser(anyString());
    }

    private LoginRequest loginRequest(String identifier) {
        LoginRequest request = new LoginRequest();
        request.setIdentifier(identifier);
        request.setPassword(RAW_PASSWORD);
        return request;
    }

    private ResendOtpRequest resendOtpRequest(UUID userId, String purpose) {
        ResendOtpRequest request = new ResendOtpRequest();
        request.setUserId(userId);
        request.setPurpose(purpose);
        return request;
    }

    private VerifyOtpRequest verifyOtpRequest(UUID userId, String purpose) {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setUserId(userId);
        request.setOtp("123456");
        request.setPurpose(purpose);
        return request;
    }

    private User registrationUser() {
        User user = activeUser();
        user.setIsVerified(false);
        user.setActive(false);
        return user;
    }

    private User activeUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName("John");
        user.setPassword(ENCODED_PASSWORD);
        user.setIsVerified(true);
        user.setActive(true);
        user.setEnabled(true);
        return user;
    }

    private UserPrincipal principalFor(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getFirstName(),
                user.getPassword(),
                true,
                List.of(new SimpleGrantedAuthority("ROLE_DONOR")));
    }
}
