package com.bd.blooddonorfinder.kafka.registry;

import com.bd.blooddonorfinder.kafka.model.topic.TopicType;
import com.bd.blooddonorfinder.utils.constants.KafkaEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class KafkaTopicRegistry {
    private final Map<String, TopicType> topicMap = new HashMap<>();

    public KafkaTopicRegistry(){
        registerTopics();
    }

    private void registerTopics(){
        register(KafkaEventType.USER_REGISTERED,
                "user.registered.v1",
                "user.registered.retry.v1",
                "user.registered.dlq.v1");

        register(KafkaEventType.USER_UPDATE,
                "user.updated.v1",
                "user.updated.retry.v1",
                "user.updated.dlq.v1");
    }

    private void register(String eventType, String mainTopic, String retryTopic, String dlqTopic){
        topicMap.put(eventType, new TopicType(mainTopic,retryTopic, dlqTopic));
    }

    public String getMainTopicToPublishEvent(String eventType) {
        TopicType topicType = topicMap.get(eventType);
        if (topicType == null) {
            throw new IllegalArgumentException("No topic configured for event type: " + eventType);
        }
        return topicType.getMainTopic();
    }

    public String getRetryTopicToPublishForEvent(String eventType) {
        TopicType topicType = topicMap.get(eventType);
        if (topicType == null) {
            throw new IllegalArgumentException("No topic configured for event type: " + eventType);
        }
        return topicType.getRetryTopic();
    }

    public String getDlqTopicToPublishEvent(String eventType) {
        TopicType topicType = topicMap.get(eventType);
        if (topicType == null) {
            throw new IllegalArgumentException("No topic configured for event type: " + eventType);
        }
        return topicType.getDlqTopic();
    }
}
