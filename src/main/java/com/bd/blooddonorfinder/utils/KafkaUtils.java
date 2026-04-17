package com.bd.blooddonorfinder.utils;

import com.bd.blooddonorfinder.utils.constants.KafkaEventType;

public class KafkaUtils {
    public static String buildTopicTypeMappings(){
        return String.join(",",
                KafkaEventType.USER_REGISTERED + ":com.bd.blooddonorfinder.kafka.model.events.UserRegisteredEvent",
                KafkaEventType.USER_UPDATE + ":com.bd.blooddonorfinder.kafka.model.events.UserUpdateEvent"
        );
    }

    public static long calculateBackoffDelay(Integer retryCount) {
        return (long) Math.pow(2, retryCount != null ? retryCount : 1) * 1000;
    }
}
