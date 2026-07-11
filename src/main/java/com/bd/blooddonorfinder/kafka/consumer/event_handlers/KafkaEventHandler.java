package com.bd.blooddonorfinder.kafka.consumer.event_handlers;

import com.bd.blooddonorfinder.kafka.model.BaseEvent;
public interface KafkaEventHandler <T extends BaseEvent>{
    String topicName();
    Class<T> eventClass();
    void  handle(T event);
}
