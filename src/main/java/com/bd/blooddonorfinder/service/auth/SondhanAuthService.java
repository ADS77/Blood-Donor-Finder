package com.bd.blooddonorfinder.service.auth;

import com.bd.blooddonorfinder.exception.*;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.AuditEvent;
import com.bd.blooddonorfinder.model.enums.OtpPurpose;
import com.bd.blooddonorfinder.payload.request.AuthRequest;
import com.bd.blooddonorfinder.payload.request.RegisterRequest;
import com.bd.blooddonorfinder.payload.request.VerifyOtpRequest;
import com.bd.blooddonorfinder.payload.response.AuthResponse;
import com.bd.blooddonorfinder.payload.response.OtpSentResponse;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.payload.response.TokenResponse;
import com.bd.blooddonorfinder.repository.auth.RoleRepository;
import com.bd.blooddonorfinder.security.AppPermissions;
import com.bd.blooddonorfinder.security.CustomUserDetailsService;
import com.bd.blooddonorfinder.security.UserPrincipal;
import com.bd.blooddonorfinder.security.context.SondhanClientContext;
import com.bd.blooddonorfinder.security.exception.InvalidJwtTokenException;
import com.bd.blooddonorfinder.security.jwt.JwtTokenProvider;
import com.bd.blooddonorfinder.service.OtpService;
import com.bd.blooddonorfinder.service.UserService;
import com.bd.blooddonorfinder.service.audit.AuditService;
import com.bd.blooddonorfinder.utils.PiiTokenizer;
import com.bd.blooddonorfinder.utils.Utils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;

@Service
@Slf4j
public class SondhanAuthService implements AuthService{

    private final UserService userService;
    private final PiiTokenizer piiTokenizer;
    private final PasswordEncoder passwordEncoder;
    private final CustomUserDetailsService userDetailsService;
    private final OtpService otpService;
    private final AuditService auditService;
    private final RoleRepository roleRepository;
    private final JwtTokenProvider tokenProvider;

    public static final String ROLE_DEFAULT = AppPermissions.ROLE_DONOR;
    public static final String ROLE_SUPER_ADMIN = AppPermissions.ROLE_SUPER_ADMIN;

    public SondhanAuthService(UserService userService,
                              PiiTokenizer piiTokenizer,
                              PasswordEncoder passwordEncoder,
                              CustomUserDetailsService userDetailsService,
                              OtpService otpService,
                              AuditService auditService,
                              RoleRepository roleRepository,
                              JwtTokenProvider tokenProvider) {
        this.userService = userService;
        this.piiTokenizer = piiTokenizer;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.otpService = otpService;
        this.auditService = auditService;
        this.roleRepository = roleRepository;
        this.tokenProvider = tokenProvider;
    }


    @Override
    @Transactional
    public RestApiResponse<AuthResponse> login(AuthRequest authRequest) {
       // User user = userService.findByPhone(authRequest.)
        return null;
    }

    @Override
    @Transactional
    public RestApiResponse<OtpSentResponse> register(RegisterRequest registerRequest, SondhanClientContext clientContext) {
        String ip = clientContext.getIpAddress();
        String userAgent = clientContext.getUserAgent();
        OtpSentResponse otpSentResponse = userService.registerNewUser(registerRequest, ip, userAgent);
        if(otpSentResponse != null
                && otpSentResponse.errorResponse()== null
                && otpSentResponse.otpExpiresInSeconds()>0){
            return com.bd.blooddonorfinder.utils.Utils.buildSuccessRestResponse(HttpStatus.OK, otpSentResponse);
        }
        return Utils.buildErrorRestResponse(
                HttpStatus.valueOf(otpSentResponse.errorResponse().getStatus()),
                otpSentResponse.errorResponse().getErrorDetails().getField(),
                otpSentResponse.errorResponse().getErrorDetails().getMessage()
        );
        //UserPrincipal principal = userDetailsService.buildPrincipal(user);

    }

    @Override
    public List<String> getUserRole(String token) {
        return null;
    }

    @Override
    public User getUserDetails(String token) {
        return null;
    }

    @Override
    public RestApiResponse<String> logout(HttpServletRequest request, HttpServletResponse response, String token, String tokenType) {
        return null;
    }

 /*   @Override
    public RestApiResponse<AuthResponse> verifyOtp(SondhanClientContext clientContext,VerifyOtpRequest verifyOtpRequest) {
        OtpPurpose purpose = Utils.parseOtpPurpose(verifyOtpRequest.getPurpose());
        User user = userService.findById(verifyOtpRequest.getUserId());
        try {
            otpService.verify(user, verifyOtpRequest.getOtp(), purpose);
            user.resetFailedOtpAttempts();
        } catch (AuthException e){
            user.incrementFailedOtpAttempts();
            userService.saveUser(user); // persist failed attempt counter / lockout
            auditService.log(AuditEvent.OTP_FAILED,
                    user.getId(),
                    clientContext.getIpAddress(),
                    clientContext.getUserAgent(),
                    "{\"purpose\":\"" + purpose + "\"}");
            throw e;
        }
        if (purpose == OtpPurpose.REGISTER) {
            userService.completeRegistrationAfterOtpVerification(user);
        } else {
            userService.saveUser(user);
        }
        Role role = roleRepository.findByName(ROLE_DEFAULT)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + ROLE_DEFAULT));
        user.addRole(role);
        userService.saveUser(user);
        User reloadUser = userService.getUserWithRolePermissions(verifyOtpRequest.getUserId());
        UserPrincipal principal = userDetailsService.buildPrincipal(reloadUser);
        TokenResponse tokenResponse = null;
        try {
            tokenResponse = tokenProvider.getTokenPair(principal);
            auditService.log(AuditEvent.OTP_VERIFIED,
                    principal.getId(),
                    clientContext.getIpAddress(),
                    clientContext.getUserAgent());
        } catch (InvalidJwtTokenException e) {
            log.error("Failed to create access and refresh token pair, error : {}", e.getMessage());
            e.printStackTrace();
        }
        AuthResponse authResponse =  tokenResponse != null
                ? AuthResponse.builder().username(principal.getUsername()).tokenResponse(tokenResponse).build()
                : AuthResponse.builder().username(principal.getUsername()).build();
        return RestApiResponse.success(authResponse,"Otp verified");
    }
*/
    @Override
    @Transactional
    public RestApiResponse<AuthResponse> verifyOtp(SondhanClientContext clientContext, VerifyOtpRequest request) {
        OtpPurpose purpose = Utils.parseOtpPurpose(request.getPurpose());
        User user = userService.findById(request.getUserId());
        validateOtpPurpose(user, purpose);
        try {
            log.debug("Verifying otp for purpose : {}...", purpose);
            otpService.verifyOtp(user, request.getOtp(), purpose);
        } catch (InvalidOtpException | OtpExpiredException  e) {
            log.warn("Failed to verify Otp for userId: {}, purpose: {}, cause: {}"
                    ,request.getUserId(), purpose, e.getCause());
            auditService.log(AuditEvent.OTP_FAILED,
                    user.getId(),
                    clientContext.getIpAddress(),
                    clientContext.getUserAgent(),
                    "{\"purpose\":\"" + purpose + "\"}");
            throw e;
        } catch (OtpAlreadyUsedException e){
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
        if(!purpose.isIssueAuthToken()){
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
        User reloadUser = userService.getUserWithRolePermissions(user.getId());
        UserPrincipal principal = userDetailsService.buildPrincipal(reloadUser);
        TokenResponse tokenResponse;
        try {
            tokenResponse = tokenProvider.getTokenPair(principal);
        } catch (InvalidJwtTokenException e) {
            log.error("Failed to create token pair for userId: {}...", user.getId(), e);
            throw new AuthenticationServiceException("Unable to complete authentication", e);
        }
        log.debug("After successful otp verification for:{}, auth token created...", purpose);
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

    private void completeOtpVerification(User user, OtpPurpose purpose) {
        switch (purpose) {
            case REGISTER ->
                    userService.completeRegistrationAfterOtpVerification(user);
            case LOGIN ->
                    userService.completeLoginAfterOtpVerification(user);
            default ->
                    throw new InvalidOtpPurposeException( ErrorCode.UNSUPPORTED_OTP_PURPOSE,"Unsupported OTP purpose: " + purpose);
        }
    }

    private void validateOtpPurpose(User user, OtpPurpose purpose) {
        switch (purpose) {
            case REGISTER -> validateRegistrationOtp(user);
            case LOGIN -> validateLoginOtp(user);
            default -> throw new InvalidOtpPurposeException(ErrorCode.UNSUPPORTED_OTP_PURPOSE,"Unsupported OTP purpose: " + purpose);
        }
    }

    private void validateRegistrationOtp(User user) {
        if (user.getIsVerified()) {
            throw new IllegalStateException("User is already registered and verified");
        }
    }

    private void validateLoginOtp(User user) {
        if (user.isLocked()) {
            throw new AccountLockedException(ErrorCode.ACCOUNT_LOCKED,"User account is locked Try again after: " + user.getLockedUntil());
        }
        if (!user.getIsVerified()) {
            throw new UserNotVerifiedException(ErrorCode.USER_NOT_VERIFIED,"User is not verified");
        }
    }


}
