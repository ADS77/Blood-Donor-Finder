package com.bd.blooddonorfinder.kafka.interfaces;

public interface NotifiableEvent {
    String getRecipientEmail();
    String getNotificationTemplate();
}
