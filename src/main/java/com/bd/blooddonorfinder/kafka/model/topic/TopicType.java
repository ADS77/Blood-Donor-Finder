package com.bd.blooddonorfinder.kafka.model.topic;

import lombok.Getter;

@Getter
public class TopicType {
    private String mainTopic;
    private String retryTopic;
    private String dlqTopic;
    public TopicType(String mainTopic, String retryTopic, String dlqTopic){
        this.mainTopic = mainTopic;
        this.retryTopic = retryTopic;
        this.dlqTopic = dlqTopic;
    }
}
