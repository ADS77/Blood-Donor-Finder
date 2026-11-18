package com.bd.blooddonorfinder.model.enums;

public enum RequestStatus {
    PENDING,
    ACCEPTED,
    /** Donor marked the donation as complete — awaiting requester approval. */
    PENDING_APPROVAL,
    /** Requester approved the donation — counters updated, chat closed. */
    FULFILLED,
    /** Requester rejected the completion claim — needs manual/admin resolution. */
    DISPUTED,
    /** TTL passed with no donor accepting — set by the escalation worker. */
    EXPIRED,
    CANCELLED;

    /**
     * Whether the request lifecycle permits a private conversation. Chat is a
     * separate state machine: this only states eligibility, not whether a chat
     * already exists. A donor accepting a request moves it into the eligible
     * range; the chat is created later, on demand, by either participant.
     */
    public boolean allowsChat() {
        return this == ACCEPTED || this == PENDING_APPROVAL || this == DISPUTED;
    }
}
