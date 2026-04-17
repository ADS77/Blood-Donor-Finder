package com.bd.blooddonorfinder.kafka.producer;

import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import com.bd.blooddonorfinder.kafka.registry.KafkaTopicRegistry;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class GenericKafkaEventProducer {

    private final KafkaTemplate<String, BaseEvent> kafkaTemplate;
    private final KafkaTopicRegistry topicRegistry;

    public <T extends BaseEvent > void publishEvent(T event){
        String topic = topicRegistry.getMainTopicToPublishEvent(event.getEventType());
        String key = event.getAggregateId();
        log.info("Publishing event: type={}, eventId={}, aggregateId={}, topic={}",
                event.getEventType(), event.getEventId(), event.getAggregateId(), topic);

        CompletableFuture<SendResult<String, BaseEvent>> future = kafkaTemplate.send(topic, key, event);
        future.whenComplete((result, ex)->{
            if(ex == null){
                log.info("Event published successfully: eventId={}, topic={}, partition={}, offset={}",
                        event.getEventId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish event: eventId={}, eventType={}, error={}",
                        event.getEventId(), event.getEventType(), ex.getMessage(), ex);
                log.info("Sending to retry");
                publishToRetryTopic(event);
            }
        });
    }

    public <T extends BaseEvent> void publishToRetryTopic(T event) {
        String retryTopic = topicRegistry.getRetryTopicToPublishForEvent(event.getEventType());

        log.info("Publishing to retry topic: eventId={}, retryCount={}, topic={}",
                event.getEventId(), event.getRetryCount(), retryTopic);

        kafkaTemplate.send(retryTopic, event.getAggregateId(), event);
    }

    public <T extends BaseEvent> void publishToDlq(T event, String errorMessage) {
        String dlqTopic = topicRegistry.getDlqTopicToPublishEvent(event.getEventType());

        log.error("Publishing to DLQ: eventId={}, eventType={}, error={}, topic={}",
                event.getEventId(), event.getEventType(), errorMessage, dlqTopic);

        kafkaTemplate.send(dlqTopic, event.getAggregateId(), event);
    }

}
