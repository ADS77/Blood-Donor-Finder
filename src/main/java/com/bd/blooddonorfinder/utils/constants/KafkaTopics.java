package com.bd.blooddonorfinder.utils.constants;

import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import com.bd.blooddonorfinder.kafka.model.events.UserRegisteredEvent;
import com.bd.blooddonorfinder.kafka.model.events.UserUpdateEvent;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum KafkaTopics {
    USER_REGISTERED("user.registered", UserRegisteredEvent.class),
    USER_UPDATE("user.updated", UserUpdateEvent.class);

    private final String topicName;
    private final Class<? extends BaseEvent> eventClass;

    KafkaTopics(String topicName, Class<? extends BaseEvent> eventClass) {
        this.topicName = topicName;
        this.eventClass = eventClass;
    }

    public String getTopicName() {
        return topicName;
    }

    public static String buildTypeMappings() {
        return Arrays.stream(values())
                .map(t -> t.name() + ":" + t.eventClass.getName())
                .collect(Collectors.joining(","));
    }
}
