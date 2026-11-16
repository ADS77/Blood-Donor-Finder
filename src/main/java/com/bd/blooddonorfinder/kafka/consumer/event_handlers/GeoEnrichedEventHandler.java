package com.bd.blooddonorfinder.kafka.consumer.event_handlers;

import com.bd.blooddonorfinder.kafka.idempotency.ProcessedEventStore;
import com.bd.blooddonorfinder.kafka.model.events.UserGeoEnrichedEvent;
import com.bd.blooddonorfinder.kafka.model.topic.KafkaTopics;
import com.bd.blooddonorfinder.kafka.sync.AbstractSyncHandler;
import com.bd.blooddonorfinder.repository.UserRepository;
import com.bd.blooddonorfinder.service.es.ElasticSearchIndexService;
import com.bd.blooddonorfinder.service.geo.DonorGeoRedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class GeoEnrichedEventHandler extends AbstractSyncHandler<UserGeoEnrichedEvent> {
    private final ElasticSearchIndexService elasticSearchIndexService;
    private final ProcessedEventStore processedEventStore;
    private final UserRepository userRepository;
    private final DonorGeoRedisService donorGeoRedisService;
    protected GeoEnrichedEventHandler(ProcessedEventStore processedEventStore,
                                      ElasticSearchIndexService elasticSearchIndexService,
                                      ProcessedEventStore processedEventStore1, UserRepository userRepository,
                                      DonorGeoRedisService donorGeoRedisService) {
        super(processedEventStore);
        this.elasticSearchIndexService = elasticSearchIndexService;
        this.processedEventStore = processedEventStore1;
        this.userRepository = userRepository;
        this.donorGeoRedisService = donorGeoRedisService;
    }
    @Value("${kafka.topic.version}")
    private String topicVersion;

    @Override
    public String topicName() {
        return KafkaTopics.USER_GEO_ENRICHED.getTopicName()+'.'+topicVersion;
    }

    @Override
    public Class<UserGeoEnrichedEvent> eventClass() {
        return UserGeoEnrichedEvent.class;
    }

    /**
     * Opens a transaction around the handler so the {@code @Modifying} geo
     * update can execute. The idempotency marker is written in its own
     * transaction by {@code JpaProcessedEventStore} and compensated on failure.
     */
    @Override
    @Transactional
    public void handle(UserGeoEnrichedEvent event) {
        super.handle(event);
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

        // Redis GEO fast path for nearby search — a cache write, never fatal
        donorGeoRedisService.upsert(event.getUserId().toString(),
                event.getLatitude().doubleValue(),
                event.getLongitude().doubleValue());
    }
}
