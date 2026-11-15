package com.bd.blooddonorfinder.config.ws;

import com.bd.blooddonorfinder.model.enums.TokenType;
import com.bd.blooddonorfinder.security.jwt.JwtTokenProvider;
import com.bd.blooddonorfinder.service.auth.TokenStorageService;
import com.bd.blooddonorfinder.utils.constants.SecurityConstants;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Validates JWTs on the STOMP layer. Browsers cannot set an Authorization
 * header on a native WebSocket handshake, so the access token travels on the
 * CONNECT frame instead (never as a query param — no token leakage into logs).
 *
 * <p>Auth parity with the HTTP chain: signature + issuer + expiry + token type
 * AND the Redis whitelist check, so a logged-out (revoked) token is rejected
 * here too. Every SUBSCRIBE is constrained to the user's own /user/queue/**
 * destinations — cross-user subscription attempts close the session.
 */
@Component
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final List<String> ALLOWED_SUBSCRIPTION_DESTINATIONS =
            List.of("/user/queue/chat", "/user/queue/chat-events", "/user/queue/notifications");

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenStorageService tokenStorageService;

    public StompAuthChannelInterceptor(JwtTokenProvider jwtTokenProvider,
                                       TokenStorageService tokenStorageService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenStorageService = tokenStorageService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            requireOwnQueueSubscription(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader(SecurityConstants.TOKEN_HEADER);
        if (authHeader == null || !authHeader.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            throw new IllegalArgumentException("Missing bearer token on STOMP CONNECT");
        }
        String token = authHeader.substring(SecurityConstants.TOKEN_PREFIX.length()).trim();
        Claims claims = jwtTokenProvider.parseAndValidateClaims(token); // throws on invalid/expired
        if (jwtTokenProvider.getTokenType(claims) != TokenType.ACCESS_TOKEN) {
            throw new IllegalArgumentException("Only access tokens may open a chat socket");
        }
        if (!tokenStorageService.isAccessTokenWhitelisted(jwtTokenProvider.getTokenId(claims))) {
            throw new IllegalArgumentException("Access token is no longer whitelisted");
        }
        UUID userId = jwtTokenProvider.getUserId(claims);
        accessor.setUser(new StompPrincipal(userId));
        log.debug("WS session authenticated for userId={}", userId);
    }

    private void requireOwnQueueSubscription(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        String destination = accessor.getDestination();
        if (user == null || destination == null
                || !ALLOWED_SUBSCRIPTION_DESTINATIONS.contains(destination)) {
            log.warn("Rejected WS subscription destination={} for principal={}", destination,
                    user != null ? user.getName() : "anonymous");
            throw new IllegalArgumentException("Subscription to this destination is not allowed");
        }
        // /user/queue/** is always scoped to the authenticated session; nothing else is allowed
    }

    /** Principal whose getName() is the userId — what user-destination routing keys on. */
    record StompPrincipal(UUID userId) implements Principal {
        @Override
        public String getName() {
            return userId.toString();
        }
    }
}
