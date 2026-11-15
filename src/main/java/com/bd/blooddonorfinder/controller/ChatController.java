package com.bd.blooddonorfinder.controller;

import com.bd.blooddonorfinder.exception.RateLimitExceededException;
import com.bd.blooddonorfinder.payload.request.SendMessageRequest;
import com.bd.blooddonorfinder.payload.response.ChatMessageDto;
import com.bd.blooddonorfinder.payload.response.ChatMessagesResponse;
import com.bd.blooddonorfinder.payload.response.ChatSummaryDto;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.security.UserPrincipal;
import com.bd.blooddonorfinder.service.ChatService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Chat REST surface. WebSocket is push-only; every write persists first here,
 * so clients can always recover via keyset backfill after a reconnect.
 */
@RestController
@RequestMapping("/api/chat")
@Slf4j
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @GetMapping
    public ResponseEntity<RestApiResponse<List<ChatSummaryDto>>> myChats(Authentication authentication) {
        UUID callerId = requireCaller(authentication);
        if (callerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<ChatSummaryDto> chats = chatService.getMyChats(callerId);
        RestApiResponse<List<ChatSummaryDto>> body = new RestApiResponse<>();
        body.getSuccess().setData(chats);
        body.setStatus(HttpStatus.OK);
        return ResponseEntity.ok(body);
    }

    /** Keyset backfill: pass {@code after} (epoch-millis cursor) to fetch newer messages. */
    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @GetMapping("/{chatId}/messages")
    public ResponseEntity<RestApiResponse<ChatMessagesResponse>> messages(@PathVariable UUID chatId,
                                                                          @RequestParam(value = "after", required = false) Long after,
                                                                          @RequestParam(value = "size", required = false) Integer size,
                                                                          Authentication authentication) {
        UUID callerId = requireCaller(authentication);
        if (callerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ChatMessagesResponse response = chatService.getMessages(chatId, callerId, after, size);
        RestApiResponse<ChatMessagesResponse> body = new RestApiResponse<>();
        body.getSuccess().setData(response);
        body.setStatus(HttpStatus.OK);
        return ResponseEntity.ok(body);
    }

    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @PostMapping("/{chatId}/messages")
    public ResponseEntity<?> send(@PathVariable UUID chatId,
                                  @Valid @RequestBody SendMessageRequest request,
                                  Authentication authentication) {
        UUID callerId = requireCaller(authentication);
        if (callerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ChatMessageDto message = chatService.sendMessage(chatId, callerId, request.getBody());
        RestApiResponse<ChatMessageDto> body = new RestApiResponse<>();
        body.getSuccess().setData(message);
        body.setStatus(HttpStatus.CREATED);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @PostMapping("/{chatId}/read")
    public ResponseEntity<RestApiResponse<String>> markRead(@PathVariable UUID chatId,
                                                            Authentication authentication) {
        UUID callerId = requireCaller(authentication);
        if (callerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        chatService.markRead(chatId, callerId);
        RestApiResponse<String> body = new RestApiResponse<>();
        body.getSuccess().setMessage("marked read");
        body.setStatus(HttpStatus.OK);
        return ResponseEntity.ok(body);
    }

    static UUID requireCaller(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        return null;
    }
}
