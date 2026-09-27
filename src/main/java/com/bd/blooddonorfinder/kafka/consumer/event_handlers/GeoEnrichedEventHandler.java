package com.bd.blooddonorfinder.kafka.consumer.event_handlers;

import com.bd.blooddonorfinder.kafka.idempotency.ProcessedEventStore;
import com.bd.blooddonorfinder.kafka.model.events.UserGeoEnrichedEvent;
import com.bd.blooddonorfinder.kafka.model.topic.KafkaTopics;
import com.bd.blooddonorfinder.kafka.sync.AbstractSyncHandler;
import com.bd.blooddonorfinder.repository.UserRepository;
import com.bd.blooddonorfinder.service.es.ElasticSearchIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GeoEnrichedEventHandler extends AbstractSyncHandler<UserGeoEnrichedEvent> {
    private final ElasticSearchIndexService elasticSearchIndexService;
    private final ProcessedEventStore processedEventStore;
    private final UserRepository userRepository;
    protected GeoEnrichedEventHandler(ProcessedEventStore processedEventStore,
                                      ElasticSearchIndexService elasticSearchIndexService,
                                      ProcessedEventStore processedEventStore1, UserRepository userRepository) {
        super(processedEventStore);
        this.elasticSearchIndexService = elasticSearchIndexService;
        this.processedEventStore = processedEventStore1;
        this.userRepository = userRepository;
    }
    @Value("${kafka.topic.version}")
    private String topicVersion;

    @Override
    public String topicName() {
        return KafkaTopics.User_GEO_ENRICHED.getTopicName()+'.'+topicVersion;
    }

    @Override
    public Class<UserGeoEnrichedEvent> eventClass() {
        return UserGeoEnrichedEvent.class;
    }

    @Override
    protected void sync(UserGeoEnrichedEvent event) {
        log.info("Updating geolocation in db for userID:{} by GeoEnrichedEventHandler", event.getUserId());
        int updated = userRepository.applyGeoEnrichment(
                event.getUserId(),
                event.getLatitude().doubleValue(),
                event.getLongitude().doubleValue(),
                event.getGeoStatus()
                );
        if(updated == 0){
            log.warn("Skipped DB geo update for userId={} — not in PROCESSING state", event.getUserId());
            return;
        }
        log.info("Updating geolocation in elastic for userId : {}", event.getUserId());
        elasticSearchIndexService.partialUpdateGeo(event.getUserId(),
                event.getLatitude(),
                event.getLongitude());
    }
}
