package com.bd.blooddonorfinder.config.kafka_config;


import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import com.bd.blooddonorfinder.kafka.model.topic.KafkaTopics;
import com.bd.blooddonorfinder.kafka.producer.GenericKafkaEventProducer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@Slf4j
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;

    @Value("${spring.kafka.consumer.max-poll-records:100}")
    private Integer maxPollRecords;

    @Value("${spring.kafka.consumer.concurrency:3}")
    private Integer concurrency;

    @Value("${spring.kafka.consumer.max-retries:3}")
    private Integer maxRetries;

    @Value("${spring.kafka.consumer.retry-interval-ms:1000}")
    private Long retryIntervalMs;

    @Value("${spring.kafka.consumer.poll.timeout}")
    private Long pollTimeout;

    @Bean
    public ConsumerFactory<String, BaseEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.bd.blooddonorfinder.kafka.model");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, BaseEvent.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
        props.put(JsonDeserializer.TYPE_MAPPINGS, KafkaTopics.buildTypeMappings());

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Error handler for user events (registered/updated/deleted). Retries
     * in-process with a fixed backoff, then dead-letters the event instead of
     * silently dropping it: a lost user.registered event leaves the donor
     * unsearchable forever (no geo write can heal a doc that never existed).
     */
    @Bean
    public DefaultErrorHandler userEventErrorHandler(GenericKafkaEventProducer eventProducer) {
        ConsumerRecordRecoverer recoverer = (record, exception) -> {
            Object value = record.value();
            if (!(value instanceof BaseEvent event)) {
                log.error("Cannot dead-letter non-user record: topic={}, partition={}, offset={}, valueType={}",
                        record.topic(), record.partition(), record.offset(),
                        value == null ? "null" : value.getClass().getName(), exception);
                return;
            }

            Throwable rootCause = rootCause(exception);
            String error = rootCause != null && rootCause.getMessage() != null
                    ? rootCause.getMessage()
                    : "unknown delivery failure";
            log.error("Dead-lettering user event: eventId={}, eventType={}, topic={}, partition={}, "
                            + "offset={}, rootCause={}",
                    event.getEventId(), event.getEventType(), record.topic(),
                    record.partition(), record.offset(), rootCause, exception);

            try {
                eventProducer.publishToDlqBlocking(event, error);
            } catch (RuntimeException publishError) {
                log.error("Could not dead-letter user event: eventId={}. The message is lost after "
                                + "exhausting retries — investigate the DLQ publisher.",
                        event.getEventId(), publishError);
            }
        };

        return new DefaultErrorHandler(recoverer, new FixedBackOff(retryIntervalMs, maxRetries));
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
    public ConcurrentKafkaListenerContainerFactory<String, BaseEvent> kafkaListenerContainerFactory(
            @Qualifier("userEventErrorHandler") DefaultErrorHandler userEventErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, BaseEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setPollTimeout(pollTimeout);
        factory.setCommonErrorHandler(userEventErrorHandler);

        return factory;
    }


}
