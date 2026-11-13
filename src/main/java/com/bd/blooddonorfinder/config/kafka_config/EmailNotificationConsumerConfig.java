package com.bd.blooddonorfinder.config.kafka_config;

import com.bd.blooddonorfinder.exception.EmailPayloadException;
import com.bd.blooddonorfinder.kafka.consumer.EmailNotificationConsumer;
import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import com.bd.blooddonorfinder.kafka.model.events.EmailNotificationEvent;
import com.bd.blooddonorfinder.kafka.outbox.EmailOutboxDeliveryTracker;
import com.bd.blooddonorfinder.kafka.producer.GenericKafkaEventProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Dedicated consumer infrastructure for {@link EmailNotificationConsumer}.
 *
 * <p>A separate container factory keeps email retry and DLQ behaviour isolated
 * from the indexing consumers. Failed messages get a bounded retry budget and
 * are then published to the versioned DLQ by the existing producer, so a
 * poison message cannot block the topic partition forever.</p>
 */
@Configuration
@Slf4j
public class EmailNotificationConsumerConfig {

    @Value("${spring.kafka.consumer.max-retries:3}")
    private Integer maxRetries;

    @Value("${spring.kafka.consumer.retry-interval-ms:1000}")
    private Long retryIntervalMs;

    @Value("${spring.kafka.consumer.poll.timeout:3000}")
    private Long pollTimeout;

    @Value("${email.outbox.consumer.concurrency:1}")
    private Integer concurrency;

    @Bean
    public DefaultErrorHandler emailNotificationErrorHandler(
            GenericKafkaEventProducer eventProducer,
            EmailOutboxDeliveryTracker deliveryTracker) {

        ConsumerRecordRecoverer recoverer = (record, exception) -> {
            Object value = record.value();
            if (!(value instanceof EmailNotificationEvent event)) {
                log.error("Cannot dead-letter non-email record: topic={}, partition={}, offset={}, valueType={}",
                        record.topic(), record.partition(), record.offset(),
                        value == null ? "null" : value.getClass().getName(), exception);
                return;
            }

            Throwable rootCause = rootCause(exception);
            String error = rootCause != null && rootCause.getMessage() != null
                    ? rootCause.getMessage()
                    : "unknown delivery failure";
            // Log the whole chain (including the listener wrapper) so the
            // actionable cause is visible instead of just "listener threw".
            log.error("Dead-lettering email notification: eventId={}, type={}, topic={}, partition={}, "
                            + "offset={}, rootCause={}",
                    event.getEventId(), event.getNotificationType(), record.topic(),
                    record.partition(), record.offset(), rootCause, exception);

            try {
                deliveryTracker.markFailed(event.getEventId(), error);
            } catch (RuntimeException trackerError) {
                log.error("Could not mark email outbox row FAILED: eventId={}",
                        event.getEventId(), trackerError);
            }
            try {
                eventProducer.publishToDlqBlocking(event, error);
            } catch (RuntimeException publishError) {
                log.error("Could not dead-letter email notification: eventId={}. The outbox row is "
                                + "marked FAILED and can be replayed manually.",
                        event.getEventId(), publishError);
            }
        };

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(retryIntervalMs, maxRetries));
        // A corrupt or undecryptable payload cannot succeed on retry.
        errorHandler.addNotRetryableExceptions(EmailPayloadException.class);
        return errorHandler;
    }

    private static Throwable rootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BaseEvent> emailNotificationListenerContainerFactory(
            ConsumerFactory<String, BaseEvent> consumerFactory,
            DefaultErrorHandler emailNotificationErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, BaseEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setPollTimeout(pollTimeout);
        factory.setCommonErrorHandler(emailNotificationErrorHandler);
        return factory;
    }
}
