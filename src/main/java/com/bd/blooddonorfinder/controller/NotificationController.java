package com.bd.blooddonorfinder.controller;

import com.bd.blooddonorfinder.payload.response.NotificationDto;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.payload.response.UnreadCountDto;
import com.bd.blooddonorfinder.security.UserPrincipal;
import com.bd.blooddonorfinder.service.NotificationService;
import com.bd.blooddonorfinder.utils.Utils;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Facebook-style notification center: bell badge + feed + read state.
 * Realtime updates arrive on WS /user/queue/notifications; this REST surface
 * is the source of truth for initial load and load-more.
 */
@RestController
@RequestMapping("/api/notifications")
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @GetMapping
    public ResponseEntity<RestApiResponse<List<NotificationDto>>> feed(
            Authentication authentication,
            @RequestParam(value = "before", required = false)
            @Min(value = 0, message = "before must be a positive epoch-millis") Long before,
            @RequestParam(value = "size", required = false) @Min(1) @Max(100) Integer size) {
        UUID callerId = requireCaller(authentication);
        if (callerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Utils.buildSuccessRestResponse(HttpStatus.OK,
                notificationService.getNotifications(callerId, before, size)));
    }

    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @GetMapping("/unread-count")
    public ResponseEntity<RestApiResponse<UnreadCountDto>> unreadCount(Authentication authentication) {
        UUID callerId = requireCaller(authentication);
        if (callerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Utils.buildSuccessRestResponse(HttpStatus.OK,
                new UnreadCountDto(notificationService.getUnreadCount(callerId))));
    }

    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @PostMapping("/{id}/read")
    public ResponseEntity<RestApiResponse<String>> markRead(@PathVariable UUID id, Authentication authentication) {
        UUID callerId = requireCaller(authentication);
        if (callerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        notificationService.markRead(callerId, id);
        return ResponseEntity.ok(Utils.buildSuccessRestResponse(HttpStatus.OK, "marked read", (String) null));
    }

    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @PostMapping("/read-all")
    public ResponseEntity<RestApiResponse<String>> markAllRead(Authentication authentication) {
        UUID callerId = requireCaller(authentication);
        if (callerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        int updated = notificationService.markAllRead(callerId);
        return ResponseEntity.ok(Utils.buildSuccessRestResponse(HttpStatus.OK,
                "marked " + updated + " notification(s) read", (String) null));
    }

    private static UUID requireCaller(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        return null;
    }
}
