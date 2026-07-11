package com.bd.blooddonorfinder.controller;

import com.bd.blooddonorfinder.model.User;
import com.bd.blooddonorfinder.payload.request.LoginRequest;
import com.bd.blooddonorfinder.payload.request.RefreshTokenRequest;
import com.bd.blooddonorfinder.payload.request.UserRegistrationRequest;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.payload.response.TokenResponse;
import com.bd.blooddonorfinder.security.exception.InvalidJwtTokenException;
import com.bd.blooddonorfinder.security.jwt.JwtTokenProvider;
import com.bd.blooddonorfinder.service.UserService;
import com.bd.blooddonorfinder.service.auth.AuthenticationService;
import com.bd.blooddonorfinder.utils.Utils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Login, token refresh, and logout")
@Slf4j
public class AuthController {

    private final AuthenticationService authService;
    private final JwtTokenProvider tokenProvider;
    private final UserService userService;

    public AuthController(AuthenticationService authService, JwtTokenProvider tokenProvider, UserService userService) {
        this.authService = authService;
        this.tokenProvider = tokenProvider;
        this.userService = userService;
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate with username and password, receive JWT token pair")
    public ResponseEntity<RestApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for user={}", request.getUsername());
        RestApiResponse<TokenResponse> apiResponse;
        try {
            TokenResponse tokens = authService.login(
                    request.getUsername(), request.getPassword());

            log.info("Login successful for user={}", request.getUsername());
            apiResponse = Utils.buildSuccessRestResponse(HttpStatus.OK, tokens);
            return ResponseEntity.status(apiResponse.getStatus()).body(apiResponse);

        } catch (UsernameNotFoundException e) {
            apiResponse = Utils.buildErrorRestResponse(HttpStatus.UNAUTHORIZED, "login","Invalid username or password");
            return ResponseEntity.status(apiResponse.getStatus()).body(apiResponse);

        } catch (BadCredentialsException e) {
            apiResponse = Utils.buildErrorRestResponse(HttpStatus.UNAUTHORIZED, "login","Invalid username or password");
            return ResponseEntity.status(apiResponse.getStatus()).body(apiResponse);

        } catch (Exception e) {
            log.error("Unexpected error during login", e);
            apiResponse = Utils.buildErrorRestResponse(HttpStatus.UNAUTHORIZED, "login","An unexpected error occurred");
            return ResponseEntity.status(apiResponse.getStatus()).body(apiResponse);
        }
    }

    @PostMapping("/register")
    @Operation(summary = "Register user ", description = "Register user with UserRegistrationRequest")
    public ResponseEntity<RestApiResponse<User>> registerUser(@Valid @RequestBody UserRegistrationRequest registrationRequest){
        log.debug("Registering user with username: {}", registrationRequest.getName());
        RestApiResponse<User> apiResponse = userService.registerUser(registrationRequest);
        return ResponseEntity.status(apiResponse.getStatus()).body(apiResponse);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh tokens", description = "Exchange a valid refresh token for a new token pair")
    public ResponseEntity<RestApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RestApiResponse<TokenResponse> apiResponse;
        try {
            TokenResponse tokens = authService.refresh(request.getRefreshToken());
            apiResponse = Utils.buildSuccessRestResponse(HttpStatus.OK, tokens);
            return ResponseEntity.status(apiResponse.getStatus()).body(apiResponse);

        } catch (InvalidJwtTokenException e) {
            log.warn("Token refresh failed: {} (reason={})", e.getMessage(), e.getReason());
            String message = switch (e.getReason()) {
                case EXPIRED    -> "Refresh token has expired — please login again";
                case REVOKED    -> "Refresh token has been revoked — please login again";
                case WRONG_TYPE -> "Invalid token type, expected a refresh token";
                case SIGNATURE_INVALID -> "Refresh token signature verification failed";
                default         -> "Invalid refresh token";
            };
            apiResponse = Utils.buildErrorRestResponse(HttpStatus.UNAUTHORIZED, "refresh",message);
            return ResponseEntity.status(apiResponse.getStatus()).body(apiResponse);

        } catch (UsernameNotFoundException e) {
            apiResponse = Utils.buildErrorRestResponse(HttpStatus.UNAUTHORIZED, "refresh","User account no longer exists");
            return ResponseEntity.status(apiResponse.getStatus()).body(apiResponse);
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revoke access and refresh tokens")
    public ResponseEntity<?> logout(HttpServletRequest request,
                                    @RequestBody(required = false) RefreshTokenRequest body) {

        String accessToken = tokenProvider.resolveToken(request);
        String refreshToken = body != null ? body.getRefreshToken() : null;

        authService.logout(accessToken, refreshToken);

        return ResponseEntity.ok().body(
                java.util.Map.of("message", "Logged out successfully"));
    }
}
