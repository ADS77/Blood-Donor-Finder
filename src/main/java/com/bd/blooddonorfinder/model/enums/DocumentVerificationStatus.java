package com.bd.blooddonorfinder.model.enums;

/** Admin review state of an uploaded document. */
public enum DocumentVerificationStatus {
    /** Awaiting admin review. */
    UNVERIFIED,
    /** Admin approved — for medical documents this verifies the donor. */
    APPROVED,
    /** Admin rejected (fake/illegible) — owner notified with the reason. */
    REJECTED
}
