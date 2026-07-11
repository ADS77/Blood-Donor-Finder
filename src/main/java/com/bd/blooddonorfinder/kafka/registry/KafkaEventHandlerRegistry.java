package com.bd.blooddonorfinder.kafka.registry;

import com.bd.blooddonorfinder.exception.UnknownEventTypeException;
import com.bd.blooddonorfinder.kafka.consumer.event_handlers.KafkaEventHandler;
import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class KafkaEventHandlerRegistry {
    private final Map<String, KafkaEventHandler<?>> handlers;
    @Value("${kafka.topic.version}")
    private String topicVersion;

    public KafkaEventHandlerRegistry(List<KafkaEventHandler<?>> handlerBeans){
        this.handlers = handlerBeans.stream()
                .collect(Collectors.toUnmodifiableMap(
                        KafkaEventHandler::topicName,
                        Function.identity(),
                        (a,b)->{
                            throw new IllegalStateException(
                                    "Duplicate handler for eventType: " + a.topicName());
                        }
                ));
    }

    @PostConstruct
    void logRegistered() {
        log.info("Registered {} event handlers: {}", handlers.size(), handlers.keySet());
        for(Map.Entry<String, KafkaEventHandler<?>> t : handlers.entrySet()){
            log.info("eh key : {}, handler : {}", t.getKey(), t.getValue().eventClass());
        }
    }

    public Optional<KafkaEventHandler<?>> resolve (String topicName){
        topicName = topicName+"."+topicVersion;
        log.info("eh key:{}", topicName);
        return  Optional.ofNullable(handlers.get(topicName));
    }


    public void handleEvent(BaseEvent event) {
        KafkaEventHandler<? extends BaseEvent> handler = handlers.get(event.getEventType());
        if (handler == null) {
            throw new UnknownEventTypeException(
                    "No handler registered for event type: " + event.getEventType());
        }
        if (!handler.eventClass().isInstance(event)) {
            throw new UnknownEventTypeException(
                    "Event %s declares type %s but is a %s, handler expects %s".formatted(
                            event.getEventId(), event.getEventType(),
                            event.getClass().getSimpleName(),
                            handler.eventClass().getSimpleName()));
        }
        @SuppressWarnings("unchecked")
        KafkaEventHandler<BaseEvent> typed = (KafkaEventHandler<BaseEvent>) handler;
        typed.handle(event);
    }
}
