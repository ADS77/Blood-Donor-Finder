package com.bd.blooddonorfinder.kafka.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEvent {
    private String eventId;
    private String eventType;
    private LocalDateTime eventTimeStamp;
    private Integer retryCount;
    private String causationId;
    private String source;

    public BaseEvent(String eventType, String eventSource){
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.eventTimeStamp = LocalDateTime.now();
        this.retryCount = 0;
        this.source = eventSource;
    }
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null) ? 1 : this.retryCount + 1;
    }

    public abstract String getAggregateId();

}
