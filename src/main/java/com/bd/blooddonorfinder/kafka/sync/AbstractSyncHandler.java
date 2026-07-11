package com.bd.blooddonorfinder.kafka.sync;

import com.bd.blooddonorfinder.kafka.consumer.event_handlers.KafkaEventHandler;
import com.bd.blooddonorfinder.kafka.idempotency.ProcessedEventStore;
import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractSyncHandler <T extends BaseEvent> implements KafkaEventHandler<T> {
    private final ProcessedEventStore processedEventStore;

    protected AbstractSyncHandler(ProcessedEventStore processedEventStore) {
        this.processedEventStore = processedEventStore;
    }

    @Override
    public void handle(T event) {

        boolean firstTime = processedEventStore.markProcessedIfAbsent(event.getEventId(), event.getTopicName(), event.getAggregateId());
        if (!firstTime) {
            log.info("Duplicate event ignored: eventId={}, type={}",
                    event.getEventId(), event.getTopicName());
            return;
        }
        try {
            log.info("Handling event -> eventId={}, topic={}, aggregateId={}",
                    event.getEventId(), event.getTopicName(), event.getAggregateId());
            sync(event);
            log.info("Handled eventId={}, topic={}, aggregateId={}",
                    event.getEventId(), event.getTopicName(), event.getAggregateId());
        } catch (RuntimeException ex) {
            processedEventStore.compensate(event.getEventId());
            throw ex;
        }
    }

    protected abstract void sync(T event);
}
