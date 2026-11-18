package com.bd.blooddonorfinder.model.enums;

public enum NotificationType {
    CHAT_MESSAGE,
    /** A request participant opened the private conversation. */
    CHAT_OPENED,
    REQUEST_ACCEPTED,
    LOCATION_RECONFIRMATION,
    /** Donor marked the donation complete — requester should approve. */
    DONATION_COMPLETED,
    /** Requester approved — donor's count and rating were updated. */
    DONATION_APPROVED,
    /** Requester disputed the completion claim — donor should resolve it. */
    DONATION_DISPUTED,
    /** Broadcast to nearby donors when an urgent request goes unanswered. */
    URGENT_REQUEST_ALERT,
    /** Admin approved a medical document — owner is now a verified donor. */
    DOCUMENT_VERIFIED
}
