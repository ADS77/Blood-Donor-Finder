package com.bd.blooddonorfinder.controller;

import com.bd.blooddonorfinder.exception.AuthException;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.enums.PushPlatform;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.security.UserPrincipal;
import com.bd.blooddonorfinder.service.push.PushTokenService;
import com.bd.blooddonorfinder.utils.Utils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Device push-token registry. Clients register their FCM token after login
 * and unregister on logout — pushes follow the device, not the account.
 */
@RestController
@RequestMapping("/api/push/tokens")
@Slf4j
public class PushTokenController {

    private final PushTokenService pushTokenService;

    public PushTokenController(PushTokenService pushTokenService) {
        this.pushTokenService = pushTokenService;
    }

    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @PostMapping
    public ResponseEntity<RestApiResponse<String>> register(
            @Valid @RequestBody RegisterPushTokenRequest body, Authentication authentication) {
        UUID userId = requireCaller(authentication);
        pushTokenService.register(userId, body.getToken(), body.getPlatform());
        return ResponseEntity.ok(Utils.buildSuccessRestResponse(
                HttpStatus.OK, "push token registered", (String) null));
    }

    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @DeleteMapping("/{token}")
    public ResponseEntity<RestApiResponse<String>> unregister(
            @PathVariable String token, Authentication authentication) {
        UUID userId = requireCaller(authentication);
        pushTokenService.unregister(userId, token);
        return ResponseEntity.ok(Utils.buildSuccessRestResponse(
                HttpStatus.OK, "push token removed", (String) null));
    }

    private static UUID requireCaller(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        throw new AuthException(ErrorCode.UNAUTHORIZED, "Authentication required");
    }

    @Data
    public static class RegisterPushTokenRequest {
        @NotBlank
        @Size(max = 4096, message = "token can't exceed 4096 characters")
        private String token;

        @NotNull
        private PushPlatform platform;
    }
}
