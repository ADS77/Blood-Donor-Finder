package com.bd.blooddonorfinder.payload.response;

/**
 * Result of a location update. {@code accepted=false} means the update was a
 * no-op (position unchanged), which is not an error for the client.
 */
public record LocationUpdateResponse(boolean accepted, String message) {
    public static LocationUpdateResponse ofAccepted() {
        return new LocationUpdateResponse(true, "Location updated");
    }
    public static LocationUpdateResponse ofUnchanged() {
        return new LocationUpdateResponse(false, "Location already up to date");
    }
}
