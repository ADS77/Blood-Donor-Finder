package com.bd.blooddonorfinder.kafka.model.events;

import com.bd.blooddonorfinder.kafka.model.BaseEvent;

public class UserUpdateEvent extends BaseEvent {
    @Override
    public String getAggregateId() {
        return null;
    }
}
