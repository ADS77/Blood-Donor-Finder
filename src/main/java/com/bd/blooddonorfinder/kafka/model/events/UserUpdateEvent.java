package com.bd.blooddonorfinder.kafka.model.events;

import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import com.bd.blooddonorfinder.model.User;
import com.bd.blooddonorfinder.utils.constants.KafkaTopics;
import lombok.*;
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class UserUpdateEvent extends BaseEvent {

    public UserUpdateEvent(String eventId){
        super(eventId, KafkaTopics.USER_UPDATE,"UserUpdateEvent");

    }

    public static UserUpdateEvent from(User user) {
        UserUpdateEvent event = new UserUpdateEvent(String.valueOf(user.getId()));
        event.setAggregateId(String.valueOf(user.getId()));
        event.setVersion(user.getVersion());
        return event;
    }

}
