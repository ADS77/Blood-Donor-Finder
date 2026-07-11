package com.bd.blooddonorfinder.utils;

import com.bd.blooddonorfinder.utils.constants.KafkaTopics;

public class KafkaUtils {
    public static String buildTopicTypeMappings(){
        return String.join(",",
                KafkaTopics.USER_REGISTERED + ":com.bd.blooddonorfinder.kafka.model.events.UserRegisteredEvent",
                KafkaTopics.USER_UPDATE + ":com.bd.blooddonorfinder.kafka.model.events.UserUpdateEvent"
        );
    }

    public static long calculateBackoffDelay(Integer retryCount) {
        return (long) Math.pow(2, retryCount != null ? retryCount : 1) * 1000;
    }
}
