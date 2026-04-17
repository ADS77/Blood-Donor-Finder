package com.bd.blooddonorfinder.kafka.consumer;

import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import com.bd.blooddonorfinder.kafka.model.EventTracker;
import com.bd.blooddonorfinder.kafka.producer.GenericKafkaEventProducer;
import com.bd.blooddonorfinder.kafka.registry.KafkaEventHandlerRegistry;
import com.bd.blooddonorfinder.repository.KafkaEventTrackerRepository;
import com.bd.blooddonorfinder.utils.KafkaUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class GenericKafkaEventConsumer {

    private final KafkaEventHandlerRegistry eventHandlerRegistry;
    private final GenericKafkaEventProducer eventProducer;
    private final KafkaEventTrackerRepository eventTrackerRepository;
    public GenericKafkaEventConsumer(KafkaEventHandlerRegistry eventHandlerRegistry,
                                     GenericKafkaEventProducer eventProducer,
                                     KafkaEventTrackerRepository eventTrackerRepository){
        this.eventHandlerRegistry = eventHandlerRegistry;
        this.eventProducer = eventProducer;
        this.eventTrackerRepository = eventTrackerRepository;
    }

    //TO-DO: Integrate Event tracker for idempotency of events
    @Value("${app.retry.max.attempts}")
    private Integer maxRetryAttempts;

    @KafkaListener(
            topics = {
                    "user.registered.v1",
                    "user.updated.v1"
            },
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeEvent(
            @Payload BaseEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
            ){
        log.info("Consumed event: type={}, eventId={}, topic={}, partition={}, offset={}",
                event.getEventType(), event.getEventId(), topic, partition, offset);
        try {
            //Check Idempotency
            if(isEventAlreadyProcessed(event.getEventId())){
                log.info("Event already processed in retry, skipping: eventId={}",
                        event.getEventId());
                acknowledgment.acknowledge();;
                return;
            }
            //Route to appropriate handler
            eventHandlerRegistry.handleEvent(event);
            markEventAsProcessed(event);
            acknowledgment.acknowledge();
            log.info("Successfully processed event: eventId={}, type={}",
                    event.getEventId(), event.getEventType());

        } catch (Exception e) {
            log.error("Error processing event: eventId={}, type={}, error={}",
                    event.getEventId(), event.getEventType(), e.getMessage(), e);

            handleFailure(event, e.getMessage(), acknowledgment);
        }

    }

    @KafkaListener(
            topics = {
                    "user.registered.retry.v1",
                    "user.updated.retry.v1"
            },
            groupId = "${spring.kafka.consumer.group-id}-retry",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeRetryEvent(
            @Payload BaseEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        log.info("Consumed retry event: type={}, eventId={}, retryCount={}, topic={}",
                event.getEventType(), event.getEventId(), event.getRetryCount(), topic);
        try {

            if (isEventAlreadyProcessed(event.getEventId())) {
                log.info("Event already processed in retry, skipping: eventId={}",
                        event.getEventId());
                acknowledgment.acknowledge();
                return;
            }
            // Exponential backoff
            Thread.sleep(KafkaUtils.calculateBackoffDelay(event.getRetryCount()));
            eventHandlerRegistry.handleEvent(event);
            markEventAsProcessed(event);
            acknowledgment.acknowledge();
            log.info("Successfully processed retry event: eventId={}", event.getEventId());

        } catch (Exception e) {
            log.error("Error processing retry event: eventId={}, error={}",
                    event.getEventId(), e.getMessage(), e);

            handleFailure(event, e.getMessage(), acknowledgment);
        }
    }

    private void markEventAsProcessed(BaseEvent event) {

        EventTracker eventTracker = EventTracker.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .status("PROCESSED")
                .build();
        eventTrackerRepository.save(eventTracker);

    }

    private void handleFailure(BaseEvent event, String errorMsg, Acknowledgment acknowledgment) {
        event.incrementRetryCount();
        if(event.getRetryCount() <= maxRetryAttempts){
            log.info("Sending to retry topic: eventId={}, retryCount={}",
                    event.getEventId(), event.getRetryCount());
            eventProducer.publishToRetryTopic(event);
            acknowledgment.acknowledge();
        } else {
            log.error("Max retry attempts exceeded, sending to DLQ: eventId={}",
                    event.getEventId());
            eventProducer.publishToDlq(event,errorMsg);
            eventTrackerRepository.save(
                    EventTracker.builder()
                            .eventId(event.getEventId())
                            .eventType(event.getEventType())
                            .status("FAILED")
                            .errorMessage(errorMsg)
                            .build()
            );
            acknowledgment.acknowledge();

        }
    }

    private boolean isEventAlreadyProcessed(String eventId) {
       return eventTrackerRepository.existsByEventId(eventId);
    }
}
