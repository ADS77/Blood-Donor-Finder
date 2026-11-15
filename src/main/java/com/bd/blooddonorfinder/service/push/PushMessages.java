package com.bd.blooddonorfinder.service.push;

import com.bd.blooddonorfinder.model.enums.NotificationType;

import java.util.Map;

/**
 * Human-readable push text per notification type. Kept as a pure util so it
 * is unit-testable and reusable by the FCM sender.
 */
final class PushMessages {

    private PushMessages() {
    }

    static String title(NotificationType type, Map<String, Object> payload) {
        return switch (type) {
            case CHAT_MESSAGE -> name(payload, "senderName") + " sent you a message";
            case REQUEST_ACCEPTED -> "A donor accepted your blood request 🩸";
            case LOCATION_RECONFIRMATION -> "Confirm your location";
            case DONATION_COMPLETED -> "Donation marked complete — please confirm";
            case DONATION_APPROVED -> "Donation confirmed! 🎉";
            case DONATION_DISPUTED -> "Donation completion disputed";
            case URGENT_REQUEST_ALERT -> urgentTitle(payload);
            case DOCUMENT_VERIFIED -> "Your document was verified ✅";
        };
    }

    static String body(NotificationType type, Map<String, Object> payload) {
        return switch (type) {
            case CHAT_MESSAGE -> str(payload, "preview", "Tap to open the chat.");
            case REQUEST_ACCEPTED -> name(payload, "donorName")
                    + " is on the way — the chat is open.";
            case LOCATION_RECONFIRMATION -> "Confirm your position so nearby patients can find you.";
            case DONATION_COMPLETED -> "Please approve the donation so the donor gets credit.";
            case DONATION_APPROVED -> "Your donation count and rating have been updated.";
            case DONATION_DISPUTED -> "The requester reported an issue — please resolve it in the app.";
            case URGENT_REQUEST_ALERT -> str(payload, "bloodGroup", "Blood") + " urgently needed"
                    + (payload.get("distanceKm") != null ? " ~" + payload.get("distanceKm") + " km away" : "");
            case DOCUMENT_VERIFIED -> "You are now a verified donor — you'll surface higher in searches.";
        };
    }

    private static String urgentTitle(Map<String, Object> payload) {
        String urgency = str(payload, "urgency", "");
        String prefix = "CRITICAL".equals(urgency) ? "🚨 CRITICAL" : "Urgent";
        return prefix + ": " + str(payload, "bloodGroup", "blood") + " needed";
    }

    private static String str(Map<String, Object> payload, String key, String fallback) {
        Object value = payload.get(key);
        return value != null && !String.valueOf(value).isBlank() ? String.valueOf(value) : fallback;
    }

    private static String name(Map<String, Object> payload, String key) {
        String name = str(payload, key, "");
        return name.isBlank() ? "Someone" : name;
    }
}
