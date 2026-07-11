package com.bd.blooddonorfinder.kafka.consumer;

import com.bd.blooddonorfinder.kafka.consumer.dispatch.KafkaEventDispatcher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.KafkaListeners;
import org.springframework.kafka.support.Acknowledgment;
import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserEventConsumer {
    private final KafkaEventDispatcher eventDispatcher;


    public UserEventConsumer(KafkaEventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
    }

    @KafkaListener(
            topics = {
                    "user.registered.${kafka.topic.version}",
                    "user.updated.${kafka.topic.version}",
                    "user.deleted.${kafka.topic.version}"
            },
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onUserEvent(BaseEvent event, Acknowledgment ack){
        log.info("Received eventId={}, topic={}, aggregateId={}, version={}, event = {}",
                event.getEventId(), event.getTopicName(),
                event.getAggregateId(), event.getVersion(), event);
        eventDispatcher.dispatch(event);
        log.info("Event dispatched successfully. eventId = {}", event.getEventId());
        ack.acknowledge();
    }
}
