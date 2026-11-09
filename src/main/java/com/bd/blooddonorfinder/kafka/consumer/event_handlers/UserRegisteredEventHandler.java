package com.bd.blooddonorfinder.kafka.consumer.event_handlers;

import com.bd.blooddonorfinder.kafka.idempotency.ProcessedEventStore;
import com.bd.blooddonorfinder.kafka.model.events.UserRegisteredEvent;
import com.bd.blooddonorfinder.kafka.sync.AbstractSyncHandler;
import com.bd.blooddonorfinder.service.es.ElasticSearchIndexService;
import com.bd.blooddonorfinder.kafka.model.topic.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Component
@Slf4j

public class UserRegisteredEventHandler extends AbstractSyncHandler<UserRegisteredEvent> {
    private final ElasticSearchIndexService elasticSearchIndexService;
    private final ProcessedEventStore eventStore;

    @Value("${kafka.topic.version}")
    private String topicVersion;

    public UserRegisteredEventHandler(ElasticSearchIndexService elasticSearchIndexService, ProcessedEventStore eventStore) {
        super(eventStore);
        this.elasticSearchIndexService = elasticSearchIndexService;
        this.eventStore = eventStore;
    }

    @Override
    public String topicName() {
        return KafkaTopics.USER_REGISTERED.getTopicName() +"."+ topicVersion;
    }

    @Override
    public Class<UserRegisteredEvent> eventClass() {
        return UserRegisteredEvent.class;
    }

    @Override
    protected void sync(UserRegisteredEvent event) {
        log.info("Sync UserRegisteredEvent to elastic index starts");
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        elasticSearchIndexService.indexRegisteredDonor(event);
        stopWatch.stop();
        log.info("UserRegisteredEvent synced into elastic, time taken : {}ms", stopWatch.getTotalTimeMillis());
    }
}
