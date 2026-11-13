package com.bd.blooddonorfinder.exception;

import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.payload.request.VerifyOtpRequest;
import com.bd.blooddonorfinder.payload.response.ApiErrorResponse;
import com.bd.blooddonorfinder.security.exception.InvalidJwtTokenException;
import com.bd.blooddonorfinder.service.auth.AuthFailureRateLimiter;
import com.bd.blooddonorfinder.utils.IpExtractor;
import com.bd.blooddonorfinder.utils.Utils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final AuthFailureRateLimiter authFailureRateLimiter;

    public GlobalExceptionHandler(AuthFailureRateLimiter authFailureRateLimiter) {
        this.authFailureRateLimiter = authFailureRateLimiter;
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthException(
            AuthException ex, HttpServletRequest request) {
        HttpStatus status = mapErrorCodeToStatus(ex.getErrorCode());
        log.warn("AuthException [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(status, ex.getErrorCode().name(), ex.getMessage(), request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserNotFound(
            UserNotFoundException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                "User not found", request);
    }

    /**
     * Catch-all. The exception message is logged server-side but never returned
     * to the client, because messages from persistence/HTTP clients routinely
     * contain SQL, hostnames, class names and other internal details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception while processing {}", request.getRequestURI(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        ResponseEntity<ApiErrorResponse> rateLimited =
                checkAuthFailureLimit(request, ex.getBindingResult().getTarget());
        if (rateLimited != null) {
            return rateLimited;
        }

        List<ApiErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> ApiErrorResponse.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .build())
                .toList();

        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("VALIDATION_FAILED")
                .message("One or more fields are invalid")
                .path(request.getRequestURI())
                .details(fieldErrors)
                .timestamp(java.time.Instant.now())
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        Object target = ex.getAllValidationResults().stream()
                .map(result -> result.getArgument())
                .filter(VerifyOtpRequest.class::isInstance)
                .findFirst()
                .orElse(null);
        ResponseEntity<ApiErrorResponse> rateLimited = checkAuthFailureLimit(request, target);
        if (rateLimited != null) {
            return rateLimited;
        }
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more fields are invalid", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        ResponseEntity<ApiErrorResponse> rateLimited = checkAuthFailureLimit(request, null);
        if (rateLimited != null) {
            return rateLimited;
        }
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more fields are invalid", request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(
            Exception ex, HttpServletRequest request) {
        ResponseEntity<ApiErrorResponse> rateLimited = checkAuthFailureLimit(request, null);
        if (rateLimited != null) {
            return rateLimited;
        }
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                "Malformed or missing request data", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}", request.getRequestURI(), ex);
        return buildErrorResponse(HttpStatus.CONFLICT, "CONFLICT",
                "The request conflicts with existing data", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(
            NoResourceFoundException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND",
                "Resource not found", request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex,
                                                                 HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Invalid username or password", request);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserNotFound(UsernameNotFoundException ex,
                                                               HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Invalid username or password", request);
    }

    @ExceptionHandler(InvalidJwtTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidToken(InvalidJwtTokenException ex,
                                                               HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                "Invalid or expired token", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                               HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You do not have permission to access this resource", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuth(AuthenticationException ex,
                                                       HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Authentication failed", request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimit(RateLimitExceededException ex,
                                                            HttpServletRequest request) {
        return buildRateLimitResponse(ex, request);
    }

    private ResponseEntity<ApiErrorResponse> buildRateLimitResponse(
            RateLimitExceededException ex, HttpServletRequest request) {
        ResponseEntity<ApiErrorResponse> response = buildErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                "Too many requests. Please try again later.", request);
        long retryAfterSeconds = ex.getRetryAfterSeconds();
        if (retryAfterSeconds <= 0) {
            return response;
        }
        return ResponseEntity.status(response.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
                .body(response.getBody());
    }

    /**
     * Counts a rejected authentication request that failed before the service
     * could run (Bean Validation or JSON deserialization). The service counts
     * every failure raised after it is entered, so this method must only be
     * called from handlers for pre-service failures.
     */
    private ResponseEntity<ApiErrorResponse> checkAuthFailureLimit(
            HttpServletRequest request, Object target) {
        AuthFailureKey key = resolveAuthFailureKey(request, target);
        if (key == null) {
            return null;
        }

        String clientIp = IpExtractor.extract(request);
        try {
            authFailureRateLimiter.checkAllowed(key.scope(), clientIp, key.subject());
        } catch (RateLimitExceededException e) {
            return buildRateLimitResponse(e, request);
        }
        authFailureRateLimiter.recordFailure(key.scope(), clientIp, key.subject());
        return null;
    }

    private AuthFailureKey resolveAuthFailureKey(HttpServletRequest request, Object target) {
        String path = request.getRequestURI();
        if (path.endsWith("/auth/login")) {
            // The user id is only known after the service resolves the
            // identifier, so pre-service failures share the per-IP bucket.
            return new AuthFailureKey("login", "unknown");
        }
        if (!path.endsWith("/auth/verify-otp")) {
            return null;
        }
        if (target instanceof VerifyOtpRequest verifyOtpRequest) {
            String subject = verifyOtpRequest.getUserId() != null
                    ? verifyOtpRequest.getUserId().toString()
                    : "unknown";
            return new AuthFailureKey(
                    "verify-otp:" + Utils.otpPurposeKey(verifyOtpRequest.getPurpose()), subject);
        }
        return new AuthFailureKey("verify-otp:INVALID", "unknown");
    }

    private record AuthFailureKey(String scope, String subject) {
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(
            HttpStatus status,
            String errorCode,
            String message,
            HttpServletRequest request) {
        ApiErrorResponse error = ApiErrorResponse.of(
                status.value(),
                errorCode,
                message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(error);
    }

    private HttpStatus mapErrorCodeToStatus(ErrorCode code) {
        return switch (code) {
            case USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case USER_ALREADY_EXISTS_WITH_PHONE,
                 USER_ALREADY_EXISTS_WITH_EMAIL,
                 OTP_ALREADY_USED,
                 ALREADY_VERIFIED -> HttpStatus.CONFLICT;
            case UNAUTHORIZED, REFRESH_REUSE_ATTACK, TOKEN_EXPIRED, TOKEN_INVALID -> HttpStatus.UNAUTHORIZED;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case ACCOUNT_LOCKED, USER_NOT_VERIFIED -> HttpStatus.FORBIDDEN;
            case OTP_SEND_FAILED -> HttpStatus.BAD_GATEWAY;
            case INVALID_OTP,
                 OTP_EXPIRED,
                 OTP_DELIVERY_UNAVAILABLE,
                 INVALID_REQUEST,
                 INVALID_EMAIL,
                 UNSUPPORTED_OTP_PURPOSE -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
