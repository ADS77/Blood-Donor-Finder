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
    CANCELLED
}
