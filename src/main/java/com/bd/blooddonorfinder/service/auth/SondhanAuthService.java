package com.bd.blooddonorfinder.service.auth;

import com.bd.blooddonorfinder.exception.*;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.AuditEvent;
import com.bd.blooddonorfinder.model.enums.OtpPurpose;
import com.bd.blooddonorfinder.model.enums.TokenType;
import com.bd.blooddonorfinder.payload.enums.LoginIdentifierType;
import com.bd.blooddonorfinder.payload.request.LoginRequest;
import com.bd.blooddonorfinder.payload.request.RegisterRequest;
import com.bd.blooddonorfinder.payload.request.VerifyOtpRequest;
import com.bd.blooddonorfinder.payload.response.AuthResponse;
import com.bd.blooddonorfinder.payload.response.OtpSentResponse;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.payload.response.TokenResponse;
import com.bd.blooddonorfinder.security.CustomUserDetailsService;
import com.bd.blooddonorfinder.security.UserPrincipal;
import com.bd.blooddonorfinder.security.context.SondhanClientContext;
import com.bd.blooddonorfinder.security.exception.InvalidJwtTokenException;
import com.bd.blooddonorfinder.security.jwt.JwtTokenProvider;
import com.bd.blooddonorfinder.service.OtpService;
import com.bd.blooddonorfinder.service.UserService;
import com.bd.blooddonorfinder.service.audit.AuditService;
import com.bd.blooddonorfinder.utils.LoginIdentifierResolver;
import com.bd.blooddonorfinder.utils.Utils;
import com.bd.blooddonorfinder.utils.constants.TokenErrorReason;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class SondhanAuthService implements AuthService {

    private static final String LOGIN_FAILED_METADATA =
            "{\"success\":false,\"reason\":\"INVALID_CREDENTIALS\"}";

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final CustomUserDetailsService userDetailsService;
    private final OtpService otpService;
    private final AuditService auditService;
    private final JwtTokenProvider tokenProvider;
    private final TokenStorageService tokenStorageService;

    /**
     * A valid BCrypt hash used to keep the response time of "unknown user"
     * and "wrong password" paths comparable. The value is generated with the
     * configured encoder so the hashing cost always matches.
     */
    private final String dummyPasswordHash;

    public SondhanAuthService(UserService userService,
                              PasswordEncoder passwordEncoder,
                              CustomUserDetailsService userDetailsService,
                              OtpService otpService,
                              AuditService auditService,
                              JwtTokenProvider tokenProvider,
                              TokenStorageService tokenStorageService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.otpService = otpService;
        this.auditService = auditService;
        this.tokenProvider = tokenProvider;
        this.tokenStorageService = tokenStorageService;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-" + UUID.randomUUID());
    }

    @Override
    @Transactional(readOnly = true)
    public RestApiResponse<AuthResponse> login(LoginRequest loginRequest, SondhanClientContext clientContext) {
        String identifier = normalizeIdentifier(loginRequest.getIdentifier());
        LoginIdentifierType identifierType = LoginIdentifierResolver.resolve(identifier);

        User user;
        try {
            user = userService.findByIdentifier(identifier, identifierType);
        } catch (AuthException | UserNotFoundException e) {
            // Run a hash comparison anyway so that an attacker cannot reliably
            // distinguish "unknown account" from "wrong password" by timing.
            passwordEncoder.matches(loginRequest.getPassword(), dummyPasswordHash);
            log.warn("Login failed: no account for identifier type={}, ip={}",
                    identifierType, clientContext.getIpAddress());
            throw new BadCredentialsException("Invalid credentials");
        }

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            auditService.log(AuditEvent.LOGIN, user.getId(),
                    clientContext.getIpAddress(), clientContext.getUserAgent(), LOGIN_FAILED_METADATA);
            log.warn("Login failed: invalid password for userId={}, ip={}",
                    user.getId(), clientContext.getIpAddress());
            throw new BadCredentialsException("Invalid credentials");
        }

        validateLoginAllowed(user, clientContext);

        UserPrincipal principal = userDetailsService.buildPrincipal(user);
        TokenResponse tokenResponse;
        try {
            tokenResponse = tokenProvider.getTokenPair(principal);
        } catch (InvalidJwtTokenException e) {
            log.error("Failed to create token pair for userId: {}", user.getId(), e);
            throw new AuthenticationServiceException("Unable to complete authentication", e);
        }

        auditService.log(AuditEvent.LOGIN, principal.getId(),
                clientContext.getIpAddress(), clientContext.getUserAgent(), "{\"success\":true}");
        log.info("Login successful for userId={}, ip={}", principal.getId(), clientContext.getIpAddress());

        AuthResponse authResponse = AuthResponse.builder()
                .username(principal.getUsername())
                .tokenResponse(tokenResponse)
                .build();
        return RestApiResponse.success(authResponse, "Login successful");
    }

    @Override
    @Transactional
    public RestApiResponse<OtpSentResponse> register(RegisterRequest registerRequest, SondhanClientContext clientContext) {
        OtpSentResponse otpSentResponse = userService.registerNewUser(
                registerRequest, clientContext.getIpAddress(), clientContext.getUserAgent());

        if (otpSentResponse == null) {
            throw new AuthException(ErrorCode.OTP_SEND_FAILED, "Failed to send OTP");
        }
        if (otpSentResponse.errorResponse() == null && otpSentResponse.otpExpiresInSeconds() > 0) {
            return Utils.buildSuccessRestResponse(HttpStatus.OK, otpSentResponse);
        }
        if (otpSentResponse.errorResponse() == null) {
            throw new AuthException(ErrorCode.OTP_SEND_FAILED, "Failed to send OTP");
        }
        return Utils.buildErrorRestResponse(
                HttpStatus.valueOf(otpSentResponse.errorResponse().getStatus()),
                otpSentResponse.errorResponse().getErrorDetails().getField(),
                otpSentResponse.errorResponse().getErrorDetails().getMessage()
        );
    }

    @Override
    public List<String> getUserRole(String token) {
        return tokenProvider.getRoles(parseToken(token));
    }

    @Override
    public User getUserDetails(String token) {
        UUID userId = tokenProvider.getUserId(parseToken(token));
        if (userId == null) {
            throw new AuthException(ErrorCode.TOKEN_INVALID, "Token does not contain a valid user id");
        }
        return userService.getUserWithRolePermissions(userId);
    }

    @Override
    public void logout(String accessToken, String refreshToken, SondhanClientContext clientContext) {
        UUID accessTokenUserId = revokeToken(accessToken);
        UUID refreshTokenUserId = revokeToken(refreshToken);
        UUID userId = accessTokenUserId != null ? accessTokenUserId : refreshTokenUserId;

        if (userId != null) {
            auditService.log(AuditEvent.LOGOUT, userId,
                    clientContext.getIpAddress(), clientContext.getUserAgent());
            log.info("Logout completed for userId={}", userId);
        } else {
            log.debug("Logout completed without a parsable access or refresh token");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshTokenString, SondhanClientContext clientContext)
            throws InvalidJwtTokenException {

        if (!StringUtils.hasText(refreshTokenString)) {
            throw new InvalidJwtTokenException("Refresh token is required", TokenErrorReason.MISSING);
        }

        Claims claims = tokenProvider.parseAndValidateClaims(refreshTokenString);
        TokenType type;
        try {
            type = tokenProvider.getTokenType(claims);
        } catch (IllegalArgumentException e) {
            throw new InvalidJwtTokenException("Malformed refresh token", e, TokenErrorReason.MALFORMED);
        }
        if (type != TokenType.REFRESH_TOKEN) {
            throw new InvalidJwtTokenException("Expected refresh token", TokenErrorReason.WRONG_TYPE);
        }

        String jti = tokenProvider.getTokenId(claims);
        UUID userId = tokenProvider.getUserId(claims);
        if (!StringUtils.hasText(jti) || userId == null) {
            throw new InvalidJwtTokenException("Malformed refresh token", TokenErrorReason.MALFORMED);
        }

        if (!tokenStorageService.isRefreshTokenWhitelisted(jti)) {
            // A validly signed refresh token that is no longer whitelisted means
            // it has already been rotated. Treat this as possible token theft.
            log.warn("Refresh token reuse detected for userId={}, jti={}", userId, jti);
            tokenStorageService.revokeAllTokensForUser(userId.toString());
            auditService.log(AuditEvent.REFRESH_REUSE_ATTACK, userId,
                    clientContext.getIpAddress(), clientContext.getUserAgent(),
                    "{\"jti\":\"" + jti + "\"}");
            throw new InvalidJwtTokenException("Refresh token has been revoked", TokenErrorReason.REVOKED);
        }

        User user;
        try {
            user = userService.getUserWithRolePermissions(userId);
        } catch (UserNotFoundException | AuthException e) {
            tokenStorageService.revokeAllTokensForUser(userId.toString());
            log.warn("Refresh rejected because user no longer exists: userId={}", userId);
            throw new InvalidJwtTokenException("User account no longer exists", TokenErrorReason.REVOKED);
        }

        if (!isAccountActive(user)) {
            tokenStorageService.revokeAllTokensForUser(userId.toString());
            log.warn("Refresh rejected because account is not active for userId={}", userId);
            throw new InvalidJwtTokenException("Account is not eligible for token refresh",
                    TokenErrorReason.REVOKED);
        }

        // Rotate: the old refresh token must never be usable again.
        tokenStorageService.revokeRefreshToken(jti);

        UserPrincipal principal = userDetailsService.buildPrincipal(user);
        TokenResponse tokenResponse = tokenProvider.getTokenPair(principal);

        auditService.log(AuditEvent.REFRESH_TOKEN_ROTATED, userId,
                clientContext.getIpAddress(), clientContext.getUserAgent(),
                "{\"oldJti\":\"" + jti + "\"}");
        log.info("Rotated refresh token for userId={}", userId);
        return tokenResponse;
    }

    @Override
    @Transactional
    public RestApiResponse<AuthResponse> verifyOtp(SondhanClientContext clientContext, VerifyOtpRequest request) {
        OtpPurpose purpose = Utils.parseOtpPurpose(request.getPurpose());
        User user = userService.findById(request.getUserId());
        validateOtpPurpose(user, purpose);
        try {
            log.debug("Verifying otp for purpose : {}...", purpose);
            otpService.verifyOtp(user, request.getOtp(), purpose);
        } catch (InvalidOtpException | OtpExpiredException e) {
            log.warn("Failed to verify Otp for userId: {}, purpose: {}, cause: {}",
                    request.getUserId(), purpose, e.getMessage());
            auditService.log(AuditEvent.OTP_FAILED,
                    user.getId(),
                    clientContext.getIpAddress(),
                    clientContext.getUserAgent(),
                    "{\"purpose\":\"" + purpose + "\"}");
            throw e;
        } catch (OtpAlreadyUsedException e) {
            auditService.log(
                    AuditEvent.OTP_REUSE_ATTEMPT,
                    user.getId(),
                    clientContext.getIpAddress(),
                    clientContext.getUserAgent(),
                    "{\"purpose\":\"" + purpose + "\"}"
            );
            throw e;
        }

        completeOtpVerification(user, purpose);
        userService.saveUser(user);

        if (!purpose.isIssueAuthToken()) {
            log.debug("Otp verification successful, purpose : {}, no auth token issued", purpose);
            auditService.log(
                    AuditEvent.OTP_VERIFIED,
                    user.getId(),
                    clientContext.getIpAddress(),
                    clientContext.getUserAgent(),
                    "{\"purpose\":\"" + purpose + "\"}"
            );
            return RestApiResponse.success(AuthResponse.builder().build(), "OTP verified");
        }

        User reloadedUser = userService.getUserWithRolePermissions(user.getId());
        UserPrincipal principal = userDetailsService.buildPrincipal(reloadedUser);
        TokenResponse tokenResponse;
        try {
            tokenResponse = tokenProvider.getTokenPair(principal);
        } catch (InvalidJwtTokenException e) {
            log.error("Failed to create token pair for userId: {}...", user.getId(), e);
            throw new AuthenticationServiceException("Unable to complete authentication", e);
        }

        log.debug("......After successful otp verification for:{}, auth token created...", purpose);
        auditService.log(AuditEvent.OTP_VERIFIED,
                principal.getId(),
                clientContext.getIpAddress(),
                clientContext.getUserAgent());

        AuthResponse authResponse = AuthResponse.builder()
                .username(principal.getUsername())
                .tokenResponse(tokenResponse)
                .build();
        return RestApiResponse.success(authResponse, "OTP verified");
    }

    private String normalizeIdentifier(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return identifier.trim();
    }

    private void validateLoginAllowed(User user, SondhanClientContext clientContext) {
        if (user.isLocked()) {
            auditService.log(AuditEvent.ACCOUNT_LOCKED, user.getId(),
                    clientContext.getIpAddress(), clientContext.getUserAgent(),
                    "{\"reason\":\"LOGIN_ATTEMPT\"}");
            throw new AccountLockedException(ErrorCode.ACCOUNT_LOCKED,
                    "User account is locked. Try again after: " + user.getLockedUntil());
        }
        if (!Boolean.TRUE.equals(user.getIsVerified())) {
            auditService.log(AuditEvent.LOGIN, user.getId(),
                    clientContext.getIpAddress(), clientContext.getUserAgent(),
                    "{\"success\":false,\"reason\":\"USER_NOT_VERIFIED\"}");
            throw new UserNotVerifiedException(ErrorCode.USER_NOT_VERIFIED, "User is not verified");
        }
        if (!isAccountActive(user)) {
            auditService.log(AuditEvent.LOGIN, user.getId(),
                    clientContext.getIpAddress(), clientContext.getUserAgent(),
                    "{\"success\":false,\"reason\":\"ACCOUNT_DISABLED\"}");
            throw new AuthException(ErrorCode.UNAUTHORIZED, "Account is disabled");
        }
    }

    private boolean isAccountActive(User user) {
        return user.isEnabled() && user.isActive();
    }

    private UUID revokeToken(String tokenString) {
        if (!StringUtils.hasText(tokenString)) {
            return null;
        }
        try {
            Claims claims = tokenProvider.parseExpiredToken(tokenString);
            String jti = tokenProvider.getTokenId(claims);
            if (!StringUtils.hasText(jti)) {
                return null;
            }
            TokenType type = tokenProvider.getTokenType(claims);
            if (type == TokenType.ACCESS_TOKEN) {
                tokenStorageService.revokeAccessToken(jti);
            } else {
                tokenStorageService.revokeRefreshToken(jti);
            }
            return tokenProvider.getUserId(claims);
        } catch (InvalidJwtTokenException | IllegalArgumentException e) {
            log.debug("Could not revoke token — it is invalid or already expired: {}", e.getMessage());
            return null;
        }
    }

    private Claims parseToken(String token) {
        try {
            return tokenProvider.parseAndValidateClaims(token);
        } catch (InvalidJwtTokenException e) {
            throw new AuthException(ErrorCode.TOKEN_INVALID, e.getMessage());
        }
    }

    private void completeOtpVerification(User user, OtpPurpose purpose) {
        switch (purpose) {
            case REGISTER -> userService.completeRegistrationAfterOtpVerification(user);
            case LOGIN -> userService.completeLoginAfterOtpVerification(user);
            default -> throw new InvalidOtpPurposeException(
                    ErrorCode.UNSUPPORTED_OTP_PURPOSE, "Unsupported OTP purpose: " + purpose);
        }
    }

    private void validateOtpPurpose(User user, OtpPurpose purpose) {
        switch (purpose) {
            case REGISTER -> validateRegistrationOtp(user);
            case LOGIN -> validateLoginOtp(user);
            default -> throw new InvalidOtpPurposeException(
                    ErrorCode.UNSUPPORTED_OTP_PURPOSE, "Unsupported OTP purpose: " + purpose);
        }
    }

    private void validateRegistrationOtp(User user) {
        if (Boolean.TRUE.equals(user.getIsVerified())) {
            throw new IllegalStateException("User is already registered and verified");
        }
    }

    private void validateLoginOtp(User user) {
        if (user.isLocked()) {
            throw new AccountLockedException(ErrorCode.ACCOUNT_LOCKED,
                    "User account is locked. Try again after: " + user.getLockedUntil());
        }
        if (!Boolean.TRUE.equals(user.getIsVerified())) {
            throw new UserNotVerifiedException(ErrorCode.USER_NOT_VERIFIED, "User is not verified");
        }
        if (!isAccountActive(user)) {
            throw new AuthException(ErrorCode.UNAUTHORIZED, "Account is disabled");
        }
    }
}
