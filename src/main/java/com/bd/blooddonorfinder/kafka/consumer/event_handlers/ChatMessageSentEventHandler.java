package com.bd.blooddonorfinder.kafka.consumer.event_handlers;

import com.bd.blooddonorfinder.kafka.idempotency.ProcessedEventStore;
import com.bd.blooddonorfinder.kafka.model.events.ChatMessageSentEvent;
import com.bd.blooddonorfinder.kafka.model.topic.KafkaTopics;
import com.bd.blooddonorfinder.kafka.sync.AbstractSyncHandler;
import com.bd.blooddonorfinder.model.enums.NotificationType;
import com.bd.blooddonorfinder.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Every persisted chat message becomes a notification (badge + feed entry).
 * No email here — the daily digest worker is the only email producer.
 * Idempotent via ProcessedEventStore, so Kafka retries never duplicate rows.
 */
@Component
@Slf4j
public class ChatMessageSentEventHandler extends AbstractSyncHandler<ChatMessageSentEvent> {

    private final NotificationService notificationService;

    protected ChatMessageSentEventHandler(ProcessedEventStore processedEventStore,
                                          NotificationService notificationService) {
        super(processedEventStore);
        this.notificationService = notificationService;
    }

    @Value("${kafka.topic.version}")
    private String topicVersion;

    @Override
    public String topicName() {
        return KafkaTopics.CHAT_MESSAGE_SENT.getTopicName() + '.' + topicVersion;
    }

    @Override
    public Class<ChatMessageSentEvent> eventClass() {
        return ChatMessageSentEvent.class;
    }

    @Override
    protected void sync(ChatMessageSentEvent event) {
        notificationService.notify(
                event.getRecipientId(),
                NotificationType.CHAT_MESSAGE,
                event.getChatId(),
                Map.of(
                        "chatId", String.valueOf(event.getChatId()),
                        "senderId", String.valueOf(event.getSenderId()),
                        "senderName", event.getSenderName() != null ? event.getSenderName() : "",
                        "preview", event.getBodyPreview() != null ? event.getBodyPreview() : ""
                ));
    }
}
