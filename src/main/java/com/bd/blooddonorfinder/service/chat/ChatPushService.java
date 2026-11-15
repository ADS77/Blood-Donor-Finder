package com.bd.blooddonorfinder.service.chat;

import com.bd.blooddonorfinder.config.ws.FanoutEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Cross-node WebSocket fanout — the piece that makes the chat horizontally scalable.
 *
 * <p>{@code SimpMessagingTemplate.convertAndSendToUser} only reaches sessions held by
 * the LOCAL node. To deliver to a user connected to ANY node, the originating node
 * publishes a {@link FanoutEnvelope} to a Redis pub/sub channel that every node
 * subscribes to; each node then attempts local delivery (a no-op for users it
 * doesn't hold). No sticky sessions, no external message broker required.
 *
 * <p>Delivery happens after the DB transaction commits so a recipient backfilling
 * over REST can never miss the message that was just pushed.
 */
@Service
@Slf4j
public class ChatPushService {

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ChatPushService(SimpMessagingTemplate messagingTemplate,
                           StringRedisTemplate redisTemplate,
                           ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Pushes {@code payload} (serialized as JSON) to the user's {@code /user}
     * destination on every node, after the current transaction (if any) commits.
     */
    public void pushAfterCommit(UUID recipientId, String destination, Object payload) {
        try {
            FanoutEnvelope envelope = buildEnvelope(recipientId, destination, payload);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        fanout(envelope);
                    }
                });
            } else {
                fanout(envelope);
            }
        } catch (Exception e) {
            log.error("Failed to enqueue WS fanout for recipient={} destination={}",
                    recipientId, destination, e);
        }
    }

    /**
     * Delivers immediately without registering a transaction synchronization.
     * Use this from inside an {@code afterCommit} callback: a synchronization
     * registered there is silently dropped because Spring has already captured
     * the callback list.
     */
    public void pushNow(UUID recipientId, String destination, Object payload) {
        try {
            fanout(buildEnvelope(recipientId, destination, payload));
        } catch (Exception e) {
            log.error("Failed to push WS fanout for recipient={} destination={}",
                    recipientId, destination, e);
        }
    }

    private FanoutEnvelope buildEnvelope(UUID recipientId, String destination, Object payload)
            throws Exception {
        return new FanoutEnvelope(recipientId, destination,
                objectMapper.writeValueAsString(payload));
    }

    /** Publishes to Redis; every node (including this one) delivers locally on receipt. */
    private void fanout(FanoutEnvelope envelope) {
        try {
            redisTemplate.convertAndSend(ChatFanoutChannel.TOPIC, objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            log.error("Redis fanout failed — falling back to local delivery for recipient={}",
                    envelope.recipientId(), e);
            deliverLocally(envelope);
        }
    }

    /** Direct local delivery — invoked by the Redis listener on every node. */
    public void deliverLocally(FanoutEnvelope envelope) {
        try {
            messagingTemplate.convertAndSendToUser(
                    envelope.recipientId().toString(), envelope.destination(), envelope.payloadJson());
        } catch (Exception e) {
            // user simply not connected to this node — expected, not an error
            log.debug("No local session for recipient={}", envelope.recipientId());
        }
    }

    /** Channel names for the WS fanout. */
    public static final class ChatFanoutChannel {
        public static final String TOPIC = "sondhan:ws:fanout";

        private ChatFanoutChannel() {
        }
    }
}
