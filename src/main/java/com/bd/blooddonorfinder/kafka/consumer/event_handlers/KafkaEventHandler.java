package com.bd.blooddonorfinder.kafka.consumer.event_handlers;

import com.bd.blooddonorfinder.kafka.model.BaseEvent;
public interface KafkaEventHandler <T extends BaseEvent>{
    void  handle(T event);
}
