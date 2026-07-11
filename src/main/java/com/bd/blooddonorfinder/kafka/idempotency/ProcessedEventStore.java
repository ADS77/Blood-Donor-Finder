package com.bd.blooddonorfinder.kafka.idempotency;

public interface ProcessedEventStore {

    boolean markProcessedIfAbsent(String eventId, String topicName, String aggregateId);

    boolean isProcessed(String eventId);

    void compensate(String eventId);
}
