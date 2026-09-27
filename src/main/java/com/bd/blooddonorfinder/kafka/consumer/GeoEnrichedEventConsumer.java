package com.bd.blooddonorfinder.kafka.consumer;

import com.bd.blooddonorfinder.kafka.consumer.dispatch.KafkaEventDispatcher;
import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GeoEnrichedEventConsumer {
    private final KafkaEventDispatcher eventDispatcher;

    public GeoEnrichedEventConsumer(KafkaEventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
    }

    @KafkaListener(
            topics = {
                    "user.geo.enriched.${kafka.topic.version}"
            },
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onGeoEnrichmentEvent(BaseEvent event, Acknowledgment ack){
        log.info("Consumed UserGeoEnrichedEvent eventId={}, topic={}, aggregateId={}, version={}, event = {}",
                event.getEventId(), event.getTopicName(),
                event.getAggregateId(), event.getVersion(), event);
        eventDispatcher.dispatch(event);
        log.info("UserGeoEnrichedEvent dispatched successfully. eventId = {}", event.getEventId());
        ack.acknowledge();
    }
}
