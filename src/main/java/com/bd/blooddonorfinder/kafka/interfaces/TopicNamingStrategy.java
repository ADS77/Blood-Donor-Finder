package com.bd.blooddonorfinder.kafka.interfaces;

public interface TopicNamingStrategy {
    String main (String baseTopic);
    String retry(String baseTopic);
    String dlq(String baseTopic);
}
