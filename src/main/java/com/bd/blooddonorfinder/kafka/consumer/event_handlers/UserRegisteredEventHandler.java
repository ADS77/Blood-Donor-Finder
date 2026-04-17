package com.bd.blooddonorfinder.kafka.consumer.event_handlers;

import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import com.bd.blooddonorfinder.kafka.model.events.UserRegisteredEvent;
import com.bd.blooddonorfinder.service.es.ElasticSearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserRegisteredEventHandler implements KafkaEventHandler{
    private final ElasticSearchIndexService elasticSearchIndexService;
    // Need to integrate Elasticsearch service
    @Override
    public void handle(BaseEvent event) {
        log.info("Handling UserRegisteredEvent: userId = {}", event.getAggregateId());
        elasticSearchIndexService.indexRegisteredDonor((UserRegisteredEvent) event);
    }
}
