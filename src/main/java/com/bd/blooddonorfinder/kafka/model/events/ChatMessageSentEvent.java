package com.bd.blooddonorfinder.kafka.model.events;

import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import com.bd.blooddonorfinder.kafka.model.topic.KafkaTopics;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

/**
 * Published after a chat message is durably persisted. Consumed to notify an
 * offline recipient by email (online recipients already got the WS push).
 */
@Getter
@Setter
@ToString(callSuper = true)
public class ChatMessageSentEvent extends BaseEvent {
    private UUID chatId;
    private UUID messageId;
    private UUID senderId;
    private String senderName;
    private UUID recipientId;
    /** First N characters of the message for the email preview. */
    private String bodyPreview;

    public ChatMessageSentEvent() {
        super(KafkaTopics.CHAT_MESSAGE_SENT, "ChatMessageSentEvent");
    }

    public static ChatMessageSentEvent of(UUID chatId, UUID messageId,
                                          UUID senderId, String senderName,
                                          UUID recipientId, String bodyPreview) {
        ChatMessageSentEvent event = new ChatMessageSentEvent();
        event.setChatId(chatId);
        event.setMessageId(messageId);
        event.setSenderId(senderId);
        event.setSenderName(senderName);
        event.setRecipientId(recipientId);
        event.setBodyPreview(bodyPreview);
        event.setAggregateId(String.valueOf(chatId));
        return event;
    }
}
