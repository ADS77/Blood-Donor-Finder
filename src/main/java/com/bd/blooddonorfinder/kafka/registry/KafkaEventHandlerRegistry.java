package com.bd.blooddonorfinder.kafka.registry;

import com.bd.blooddonorfinder.kafka.consumer.event_handlers.KafkaEventHandler;
import com.bd.blooddonorfinder.kafka.consumer.event_handlers.UserRegisteredEventHandler;
import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import com.bd.blooddonorfinder.utils.constants.KafkaEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class KafkaEventHandlerRegistry {
    private final Map<String, KafkaEventHandler<? extends BaseEvent>> handlers = new HashMap<>();
    private final UserRegisteredEventHandler userRegisteredEventHandler;
    public KafkaEventHandlerRegistry(UserRegisteredEventHandler eventHandler){
        this.userRegisteredEventHandler = eventHandler;
        registerEventHandler(KafkaEventType.USER_REGISTERED, userRegisteredEventHandler);

        log.info("Registered {} event handlers", handlers.size());
    }

    private <T extends  BaseEvent> void registerEventHandler(String eventType, KafkaEventHandler<T> eventHandler) {
        handlers.put(eventType, eventHandler);
    }

    public <T extends BaseEvent > void handleEvent(T event){
        KafkaEventHandler<T> handler = (KafkaEventHandler<T>) handlers.get(event.getEventType());
        if(handler == null){
            throw new IllegalArgumentException(
                    "No handler registered for event type: " + event.getEventType());
        }
        log.debug("Routing event to handler: eventType={}, handler={}",
                event.getEventType(), handler.getClass().getSimpleName());
        handler.handle(event);
    }
}
