package com.bd.blooddonorfinder.kafka.registry;

import com.bd.blooddonorfinder.kafka.interfaces.TopicNamingStrategy;
import com.bd.blooddonorfinder.kafka.model.topic.TopicType;
import com.bd.blooddonorfinder.utils.constants.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class KafkaTopicRegistry {
    private Map<String, TopicType> topicMap = new HashMap<>();

    public KafkaTopicRegistry(TopicNamingStrategy topicNamingStrategy){
        this.topicMap = Arrays.stream(KafkaTopics.values())
                .collect(Collectors.toUnmodifiableMap(
                        KafkaTopics::getTopicName,
                        e-> registerTopics(e, topicNamingStrategy)
                ));
    }

    private TopicType registerTopics(KafkaTopics topicStore, TopicNamingStrategy topicNamingStrategy) {
        String topicName = topicStore.getTopicName();
        log.debug("Registering topic with name : {}", topicName);
        return new TopicType(
                topicNamingStrategy.main(topicName),
                topicNamingStrategy.retry(topicName),
                topicNamingStrategy.dlq(topicName)
        );
    }

  /*  private void register(String eventType, String mainTopic, String retryTopic, String dlqTopic){
        topicMap.put(eventType, new TopicType(mainTopic,retryTopic, dlqTopic));
    }*/

    public String getMainTopicToPublishEvent(String eventType) {
        log.debug("Came to publish event in main topic named : {}", eventType);
        return resolve(eventType).getMainTopic();
    }

    public String getRetryTopicToPublishEvent(String eventType) {
       return resolve(eventType).getRetryTopic();
    }

    public String getDlqTopicToPublishEvent(String eventType) {
        return resolve(eventType).getDlqTopic();
    }

    private TopicType resolve(String eventType){
        TopicType topicType = topicMap.get(eventType);
        if (topicType == null) {
            throw new IllegalArgumentException("No topic configured for event type: " + eventType);
        }
        return topicType;
    }
}
