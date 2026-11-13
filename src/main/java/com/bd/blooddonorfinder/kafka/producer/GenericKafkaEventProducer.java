package com.bd.blooddonorfinder.kafka.producer;

import com.bd.blooddonorfinder.exception.KafkaPublishException;
import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import com.bd.blooddonorfinder.kafka.registry.KafkaTopicRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
public class GenericKafkaEventProducer {

    private final KafkaTemplate<String, BaseEvent> kafkaTemplate;
    private final KafkaTopicRegistry topicRegistry;
    @Value("${kafka.producer.max-publish-retries}")
    private int maxPublishRetries;

    @Value("${kafka.producer.publish-timeout-ms:10000}")
    private long publishTimeoutMs;

    public GenericKafkaEventProducer(@Qualifier("eventKafkaTemplate") KafkaTemplate<String, BaseEvent> kafkaTemplate,
                                     KafkaTopicRegistry kafkaTopicRegistry){
        this.kafkaTemplate = kafkaTemplate;
        this.topicRegistry = kafkaTopicRegistry;
    }

    public <T extends BaseEvent > void publishEvent(T event){
        String topic = topicRegistry.getMainTopicToPublishEvent(event.getTopicName());
        sendTo(topic, event, true);
    }

    /**
     * Publishes an event and waits for the broker acknowledgement.
     *
     * <p>The transactional outbox relay must only mark a row PUBLISHED after
     * Kafka has durably accepted it. Unlike {@link #publishEvent(BaseEvent)},
     * which is fire-and-forget, this method surfaces broker failures to the
     * caller so it can apply retry/backoff semantics.</p>
     *
     * @throws KafkaPublishException when the send fails or times out
     */
    public <T extends BaseEvent> void publishEventBlocking(T event) {
        String topic = topicRegistry.getMainTopicToPublishEvent(event.getTopicName());
        try {
            var result = kafkaTemplate.send(topic, event.getAggregateId(), event)
                    .get(publishTimeoutMs, TimeUnit.MILLISECONDS);
            var metadata = result.getRecordMetadata();
            log.info("Event acknowledged by Kafka: eventId={}, topic={}, partition={}, offset={}",
                    event.getEventId(), metadata.topic(), metadata.partition(), metadata.offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaPublishException(
                    "Interrupted while publishing event " + event.getEventId(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new KafkaPublishException(
                    "Failed to publish event " + event.getEventId(), e);
        } catch (RuntimeException e) {
            throw new KafkaPublishException(
                    "Failed to publish event " + event.getEventId(), e);
        }
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

    /**
     * Dead-letters an event and waits for the broker acknowledgement. Used by
     * the consumer error handler so a poison message is not silently dropped
     * when the DLQ is temporarily unavailable.
     */
    public <T extends BaseEvent> void publishToDlqBlocking(T event, String errorMessage) {
        String dlqTopic = topicRegistry.getDlqTopicToPublishEvent(event.getTopicName());
        try {
            kafkaTemplate.send(dlqTopic, event.getAggregateId(), event)
                    .get(publishTimeoutMs, TimeUnit.MILLISECONDS);
            log.error("Event dead-lettered: eventId={}, eventType={}, topic={}, error={}",
                    event.getEventId(), event.getEventType(), dlqTopic, errorMessage);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaPublishException(
                    "Interrupted while dead-lettering event " + event.getEventId(), e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new KafkaPublishException(
                    "Failed to dead-letter event " + event.getEventId(), e);
        }
    }

}
