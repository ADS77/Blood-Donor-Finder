package com.bd.blooddonorfinder.model.enums;

/**
 * How fast a blood request needs donors. Drives the auto-expiry TTL and the
 * escalation cadence of the alert worker.
 */
public enum RequestUrgency {
    /** Normal request — broad alert once, expires in days. */
    ROUTINE,
    /** Time-sensitive — re-broadcast hourly within a widening radius. */
    URGENT,
    /** Life-threatening — re-broadcast every few minutes, widest radius. */
    CRITICAL
}
