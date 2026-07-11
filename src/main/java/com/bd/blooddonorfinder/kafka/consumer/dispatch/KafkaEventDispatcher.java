package com.bd.blooddonorfinder.kafka.consumer.dispatch;

import com.bd.blooddonorfinder.exception.UnknownEventTypeException;
import com.bd.blooddonorfinder.kafka.consumer.event_handlers.KafkaEventHandler;
import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import com.bd.blooddonorfinder.kafka.registry.KafkaEventHandlerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KafkaEventDispatcher {
    private final KafkaEventHandlerRegistry eventHandlerRegistry;

    public KafkaEventDispatcher(KafkaEventHandlerRegistry eventHandlerRegistry) {
        this.eventHandlerRegistry = eventHandlerRegistry;
    }

    public void dispatch(BaseEvent event){
        KafkaEventHandler<BaseEvent> handler = (KafkaEventHandler<BaseEvent>) eventHandlerRegistry
                .resolve(event.getTopicName())
                .orElseThrow(()-> new UnknownEventTypeException(event.getTopicName()));

        log.debug("Dispatching eventId={} to handler={}", event.getEventId(), handler.eventClass());
        handler.handle(event);
    }
}
