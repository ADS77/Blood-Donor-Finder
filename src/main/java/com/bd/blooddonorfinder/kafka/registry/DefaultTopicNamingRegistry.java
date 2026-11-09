package com.bd.blooddonorfinder.kafka.registry;

import com.bd.blooddonorfinder.kafka.interfaces.TopicNamingStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DefaultTopicNamingRegistry implements TopicNamingStrategy {
    @Value("${kafka.topic.version}")
    private String VERSION;
    @Override
    public String main(String baseTopic) {
        return "%s.%s".formatted(baseTopic, VERSION);
    }

    @Override
    public String retry(String baseTopic) {
        return "%s.retry.%s".formatted(baseTopic, VERSION);
    }

    @Override
    public String dlq(String baseTopic) {
        return "%s.dlq.%s".formatted(baseTopic, VERSION);
    }
}
