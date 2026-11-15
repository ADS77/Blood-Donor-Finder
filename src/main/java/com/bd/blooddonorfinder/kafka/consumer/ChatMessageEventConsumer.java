package com.bd.blooddonorfinder.kafka.consumer;

import com.bd.blooddonorfinder.kafka.consumer.dispatch.KafkaEventDispatcher;
import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ChatMessageEventConsumer {
    private final KafkaEventDispatcher eventDispatcher;

    public ChatMessageEventConsumer(KafkaEventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
    }

    @KafkaListener(
            topics = {
                    "chat.message.sent.${kafka.topic.version}"
            },
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onChatMessageSentEvent(BaseEvent event, Acknowledgment ack){
        log.info("Consumed ChatMessageSentEvent eventId={}, aggregateId={}",
                event.getEventId(), event.getAggregateId());
        eventDispatcher.dispatch(event);
        ack.acknowledge();
    }
}
