package com.bd.blooddonorfinder.kafka.model;

import com.bd.blooddonorfinder.kafka.model.topic.KafkaTopics;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEvent {
    private String eventId;
    private String topicName;
    private String eventType;
    private Instant eventTimeStamp;
    private Integer retryCount = 0;
    private long version;
    private String aggregateId;
    private String causationId;
    private String source;

    public BaseEvent(KafkaTopics topic, String eventSource){
        this.eventId = UUID.randomUUID().toString();
        this.topicName = topic.getTopicName();
        this.eventType = topic.name();
        this.eventTimeStamp = Instant.now();
        this.retryCount = 0;
        this.source = eventSource;
    }
    public void incrementRetryCount() {
        this.retryCount++;
    }


}
