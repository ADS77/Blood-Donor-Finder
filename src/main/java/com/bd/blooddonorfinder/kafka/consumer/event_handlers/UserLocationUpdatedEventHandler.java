package com.bd.blooddonorfinder.kafka.consumer.event_handlers;

import com.bd.blooddonorfinder.kafka.idempotency.ProcessedEventStore;
import com.bd.blooddonorfinder.kafka.model.events.UserLocationUpdatedEvent;
import com.bd.blooddonorfinder.kafka.model.topic.KafkaTopics;
import com.bd.blooddonorfinder.kafka.sync.AbstractSyncHandler;
import com.bd.blooddonorfinder.repository.UserRepository;
import com.bd.blooddonorfinder.service.es.ElasticSearchIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Applies a donor's confirmed position to the database and Elasticsearch.
 * Idempotent: replays of the same event are dropped by ProcessedEventStore,
 * and the DB update itself is a no-op-safe absolute set of coordinates.
 */
@Component
@Slf4j
public class UserLocationUpdatedEventHandler extends AbstractSyncHandler<UserLocationUpdatedEvent> {

    private final UserRepository userRepository;
    private final ElasticSearchIndexService elasticSearchIndexService;

    protected UserLocationUpdatedEventHandler(ProcessedEventStore processedEventStore,
                                              UserRepository userRepository,
                                              ElasticSearchIndexService elasticSearchIndexService) {
        super(processedEventStore);
        this.userRepository = userRepository;
        this.elasticSearchIndexService = elasticSearchIndexService;
    }

    @Value("${kafka.topic.version}")
    private String topicVersion;

    @Override
    public String topicName() {
        return KafkaTopics.USER_LOCATION_UPDATED.getTopicName() + '.' + topicVersion;
    }

    @Override
    public Class<UserLocationUpdatedEvent> eventClass() {
        return UserLocationUpdatedEvent.class;
    }

    @Override
    protected void sync(UserLocationUpdatedEvent event) {
        log.info("Applying confirmed location for userId={} (geoUpdatedAt={})",
                event.getUserId(), event.getGeoUpdatedAt());

        // DB first (source of truth). Ingest already wrote this row on the happy path;
        // this makes event replays and cross-instance recovery consistent.
        Instant geoUpdatedAt = Instant.ofEpochMilli(event.getGeoUpdatedAt());
        int updated = userRepository.applyLocationUpdate(
                event.getUserId(),
                event.getLatitude().doubleValue(),
                event.getLongitude().doubleValue(),
                geoUpdatedAt);
        if (updated == 0) {
            log.warn("Location update touched no rows for userId={} — user may have been deleted", event.getUserId());
            return;
        }

        elasticSearchIndexService.partialUpdateGeo(
                event.getUserId(),
                event.getLatitude(),
                event.getLongitude());
    }
}
