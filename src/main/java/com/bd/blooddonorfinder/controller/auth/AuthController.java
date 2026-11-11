package com.bd.blooddonorfinder.controller.auth;

import com.bd.blooddonorfinder.payload.request.LoginRequest;
import com.bd.blooddonorfinder.payload.request.RefreshTokenRequest;
import com.bd.blooddonorfinder.payload.request.RegisterRequest;
import com.bd.blooddonorfinder.payload.request.VerifyOtpRequest;
import com.bd.blooddonorfinder.payload.response.AuthResponse;
import com.bd.blooddonorfinder.payload.response.OtpSentResponse;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.payload.response.TokenResponse;
import com.bd.blooddonorfinder.security.context.SondhanClientContext;
import com.bd.blooddonorfinder.security.exception.InvalidJwtTokenException;
import com.bd.blooddonorfinder.security.jwt.JwtTokenProvider;
import com.bd.blooddonorfinder.service.auth.AuthService;
import com.bd.blooddonorfinder.utils.Utils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Login, register, token refresh, and logout")
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider tokenProvider;

    public AuthController(AuthService authService, JwtTokenProvider tokenProvider) {
        this.authService = authService;
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate with email/phone and password, receive JWT token pair")
    public ResponseEntity<RestApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest httpRequest) {

        log.info("Login attempt with email/phone = {}", loginRequest.getIdentifier());
        SondhanClientContext clientContext = Utils.buildClientContext(httpRequest);

        try {
            AuthResponse authResponse = authService.login(loginRequest, clientContext)
                    .getSuccess()
                    .getData();
            RestApiResponse<TokenResponse> apiResponse = Utils.buildSuccessRestResponse(
                    HttpStatus.OK, authResponse.getTokenResponse());
            return ResponseEntity.status(apiResponse.getStatus()).body(apiResponse);
        } catch (UsernameNotFoundException | BadCredentialsException e) {
            RestApiResponse<TokenResponse> apiResponse = Utils.buildErrorRestResponse(
                    HttpStatus.UNAUTHORIZED, "login", "Invalid username or password");
            return ResponseEntity.status(apiResponse.getStatus()).body(apiResponse);
        }
    }

    @PostMapping("/register")
    @Operation(summary = "Register user", description = "Register user with UserRegistrationRequest")
    public ResponseEntity<RestApiResponse<OtpSentResponse>> registerUser(
            @Valid @RequestBody RegisterRequest registerRequest,
            HttpServletRequest httpRequest) {

        log.debug("Registering {} {}", registerRequest.getFirstName(), registerRequest.getLastName());
        SondhanClientContext clientContext = Utils.buildClientContext(httpRequest);
        RestApiResponse<OtpSentResponse> response = authService.register(registerRequest, clientContext);
        return ResponseEntity.status(response.getStatus()).body(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh tokens", description = "Exchange a valid refresh token for a new token pair")
    public ResponseEntity<RestApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {

        SondhanClientContext clientContext = Utils.buildClientContext(httpRequest);
        RestApiResponse<TokenResponse> apiResponse;
        try {
            TokenResponse tokens = authService.refresh(request.getRefreshToken(), clientContext);
            apiResponse = Utils.buildSuccessRestResponse(HttpStatus.OK, tokens);
            return ResponseEntity.status(apiResponse.getStatus()).body(apiResponse);

        } catch (InvalidJwtTokenException e) {
            log.warn("Token refresh failed: {} (reason={})", e.getMessage(), e.getReason());
            String message = switch (e.getReason()) {
                case EXPIRED -> "Refresh token has expired — please login again";
                case REVOKED -> "Refresh token has been revoked — please login again";
                case WRONG_TYPE -> "Invalid token type, expected a refresh token";
                case SIGNATURE_INVALID -> "Refresh token signature verification failed";
                case MISSING -> "Refresh token is required";
                default -> "Invalid refresh token";
            };
            apiResponse = Utils.buildErrorRestResponse(HttpStatus.UNAUTHORIZED, "refresh", message);
            return ResponseEntity.status(apiResponse.getStatus()).body(apiResponse);

        } catch (UsernameNotFoundException e) {
            apiResponse = Utils.buildErrorRestResponse(
                    HttpStatus.UNAUTHORIZED, "refresh", "User account no longer exists");
            return ResponseEntity.status(apiResponse.getStatus()).body(apiResponse);
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revoke access and refresh tokens")
    public ResponseEntity<Map<String, String>> logout(
            HttpServletRequest request,
            @RequestBody(required = false) RefreshTokenRequest body) {

        String accessToken = tokenProvider.resolveToken(request);
        String refreshToken = body != null ? body.getRefreshToken() : null;
        SondhanClientContext clientContext = Utils.buildClientContext(request);

        authService.logout(accessToken, refreshToken, clientContext);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP", description = "Verify OTP for registration or 2FA login")
    public ResponseEntity<RestApiResponse<AuthResponse>> verifyOtp(
            @NotNull @Valid @RequestBody VerifyOtpRequest verifyOtpRequest,
            HttpServletRequest httpServletRequest) {

        log.debug("Verifying otp for userId: {}, purpose: {}",
                verifyOtpRequest.getUserId(), verifyOtpRequest.getPurpose());
        SondhanClientContext clientContext = Utils.buildClientContext(httpServletRequest);
        RestApiResponse<AuthResponse> response = authService.verifyOtp(clientContext, verifyOtpRequest);
        return ResponseEntity.status(response.getStatus()).body(response);
    }
}
