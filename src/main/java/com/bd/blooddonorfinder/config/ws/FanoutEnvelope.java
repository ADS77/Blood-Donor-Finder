package com.bd.blooddonorfinder.config.ws;

/**
 * Envelope published to the Redis fanout channel. {@code payloadJson} is
 * pre-serialized so every node forwards it verbatim to local STOMP sessions.
 */
public record FanoutEnvelope(java.util.UUID recipientId, String destination, String payloadJson) {
}
