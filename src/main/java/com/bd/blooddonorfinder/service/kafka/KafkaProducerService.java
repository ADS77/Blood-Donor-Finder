package com.bd.blooddonorfinder.service.kafka;

import com.bd.blooddonorfinder.kafka.model.events.UserRegisteredEvent;
public interface KafkaProducerService {

    public void publishUserRegisteredEvent(UserRegisteredEvent event);

    public void publishToRetryTopic (UserRegisteredEvent event);

    public void publishToDlq(UserRegisteredEvent event);
}
