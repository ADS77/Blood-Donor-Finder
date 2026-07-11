package com.bd.blooddonorfinder.kafka.producer;

import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import com.bd.blooddonorfinder.kafka.registry.KafkaTopicRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GenericKafkaEventProducer {

    private final KafkaTemplate<String, BaseEvent> kafkaTemplate;
    private final KafkaTopicRegistry topicRegistry;
    @Value("${kafka.producer.max-publish-retries}")
    private int maxPublishRetries;

    public GenericKafkaEventProducer(@Qualifier("eventKafkaTemplate") KafkaTemplate<String, BaseEvent> kafkaTemplate,
                                     KafkaTopicRegistry kafkaTopicRegistry){
        this.kafkaTemplate = kafkaTemplate;
        this.topicRegistry = kafkaTopicRegistry;
    }

    public <T extends BaseEvent > void publishEvent(T event){
        String topic = topicRegistry.getMainTopicToPublishEvent(event.getTopicName());
        sendTo(topic, event, true);
    }

    private <T extends BaseEvent> void sendTo(String topic, T event, boolean retryable) {
        log.info("Publishing event to kafka:eventId={}, aggregateId={}, topic={}",
                 event.getEventId(), event.getAggregateId(), topic);
        kafkaTemplate.send(topic, event.getAggregateId(), event)
                .whenComplete((result, ex)->{
                    if(ex == null){
                        var md = result.getRecordMetadata();
                        log.info("Event published: eventId={}, topic={}, partition={}, offset={}",
                                event.getEventId(), md.topic(), md.partition(), md.offset());
                    }else {
                        log.error("Publish failed: eventId={}, type={}, error={}",
                                event.getEventId(), event.getTopicName(), ex.getMessage(), ex);
                        if (retryable && event.getRetryCount() < maxPublishRetries) {
                            publishToRetryTopic(event);
                        } else {
                            publishToDlq(event, ex.getMessage());
                        }
                    }
                });
    }

    public <T extends BaseEvent> void publishToRetryTopic(T event) {
        event.incrementRetryCount();
        String retryTopic = topicRegistry.getRetryTopicToPublishEvent(event.getTopicName());

        log.info("Publishing to retry topic: eventId={}, retryCount={}, topic={}",
                event.getEventId(), event.getRetryCount(), retryTopic);

        sendTo(retryTopic, event, false);
    }

    public <T extends BaseEvent> void publishToDlq(T event, String errorMessage) {
        String dlqTopic = topicRegistry.getDlqTopicToPublishEvent(event.getTopicName());

        log.error("Publishing to DLQ: eventId={}, eventType={}, error={}, topic={}",
                event.getEventId(), event.getTopicName(), errorMessage, dlqTopic);

        kafkaTemplate.send(dlqTopic, event.getAggregateId(), event);
    }

}
