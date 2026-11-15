package com.bd.blooddonorfinder.config.ws;

import com.bd.blooddonorfinder.config.cors_config.CorsProperties;
import com.bd.blooddonorfinder.service.chat.ChatPushService;
import com.bd.blooddonorfinder.service.chat.PresenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.UUID;

/**
 * STOMP-over-WebSocket for the realtime chat push channel.
 *
 * <p>Sends happen over REST (persist-first); this socket is purely the push
 * surface. Cross-node delivery rides on Redis pub/sub, so instances stay
 * stateless behind any load balancer (no sticky sessions).
 */
@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final CorsProperties corsProperties;
    private final PresenceService presenceService;
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    public WebSocketConfig(CorsProperties corsProperties,
                           PresenceService presenceService,
                           StompAuthChannelInterceptor stompAuthChannelInterceptor) {
        this.corsProperties = corsProperties;
        this.presenceService = presenceService;
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // native WebSocket — JWT rides on the STOMP CONNECT frame
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(corsProperties.getAllowedOrigins().toArray(String[]::new));
        // SockJS fallback for restrictive networks/proxies
        registry.addEndpoint("/ws-sockjs")
                .setAllowedOriginPatterns(corsProperties.getAllowedOrigins().toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // in-memory broker; cross-node fanout is handled by ChatPushService (Redis pub/sub).
        // NOTE: user destinations translate /user/queue/chat → /queue/chat internally,
        // so the simple broker must own /queue, not /user.
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // small frames only — chat bodies are capped far below this
        registration.setMessageSizeLimit(16 * 1024);
        registration.setSendBufferSizeLimit(512 * 1024);
        registration.setSendTimeLimit(10_000);
    }

    /** Redis pub/sub subscription so every node receives fanout envelopes. */
    @Bean
    public RedisMessageListenerContainer wsFanoutListenerContainer(RedisConnectionFactory connectionFactory,
                                                                   ChatPushService chatPushService,
                                                                   ObjectMapper objectMapper) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            try {
                FanoutEnvelope envelope = objectMapper.readValue(message.getBody(), FanoutEnvelope.class);
                chatPushService.deliverLocally(envelope);
            } catch (Exception e) {
                log.error("Failed to process WS fanout envelope", e);
            }
        }, new ChannelTopic(ChatPushService.ChatFanoutChannel.TOPIC));
        return container;
    }

    /** Presence bookkeeping: keyed by userId, guarded by session id, TTL-backed. */
    @EventListener
    public void onSessionConnected(SessionConnectedEvent event) {
        UUID userId = extractUserId(event.getUser());
        String sessionId = event.getMessage() != null
                ? org.springframework.messaging.simp.stomp.StompHeaderAccessor
                        .wrap(event.getMessage()).getSessionId()
                : null;
        if (userId != null && sessionId != null) {
            presenceService.connect(userId, sessionId);
            log.debug("WS presence set for userId={} session={}", userId, sessionId);
        }
    }

    @EventListener
    public void onSessionDisconnected(SessionDisconnectEvent event) {
        UUID userId = extractUserId(event.getUser());
        if (userId != null) {
            presenceService.disconnect(userId, event.getSessionId());
            log.debug("WS presence cleared for userId={} session={}", userId, event.getSessionId());
        }
    }

    private UUID extractUserId(Principal principal) {
        if (principal == null) {
            return null;
        }
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
