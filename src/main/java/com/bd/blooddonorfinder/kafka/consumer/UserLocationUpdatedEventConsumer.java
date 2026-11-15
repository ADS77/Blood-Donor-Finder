package com.bd.blooddonorfinder.kafka.consumer;

import com.bd.blooddonorfinder.kafka.consumer.dispatch.KafkaEventDispatcher;
import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserLocationUpdatedEventConsumer {
    private final KafkaEventDispatcher eventDispatcher;

    public UserLocationUpdatedEventConsumer(KafkaEventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
    }

    @KafkaListener(
            topics = {
                    "user.location.updated.${kafka.topic.version}"
            },
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onUserLocationUpdatedEvent(BaseEvent event, Acknowledgment ack){
        log.info("Consumed UserLocationUpdatedEvent eventId={}, topic={}, aggregateId={}",
                event.getEventId(), event.getTopicName(), event.getAggregateId());
        eventDispatcher.dispatch(event);
        ack.acknowledge();
    }
}
