package com.bd.blooddonorfinder.model.enums;

/**
 * Business category of an email notification. It is carried in the event
 * envelope so operators can observe traffic without decrypting message
 * content.
 */
public enum EmailNotificationType {
    OTP,
    DONOR_ALERT,
    GENERAL
}
