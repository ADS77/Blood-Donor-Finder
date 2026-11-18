package com.bd.blooddonorfinder.payload.response;

import com.bd.blooddonorfinder.model.BloodRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Safe view of a blood request for clients. JPA {@link BloodRequest} entities
 * embed the full requester {@link com.bd.blooddonorfinder.model.common.User}
 * (token references, credentials fields, lazy associations) and are never
 * serialized to clients.
 *
 * @param requester     owner summary only — contact happens through the chat
 *                      opened after a donor accepts
 * @param chatAvailable whether the caller may open a conversation for this
 *                      request. It is a capability, not proof that a chat row
 *                      exists; opening is idempotent and stays in the chat API.
 */
public record BloodRequestViewDto(
        UUID id,
        String status,
        String bloodGroup,
        Integer requiredQuantity,
        String urgency,
        String message,
        GeoLocationView location,
        RequesterView requester,
        UUID acceptedDonorId,
        LocalDateTime requestTime,
        LocalDateTime expiresAt,
        boolean chatAvailable
) {

    /** Lat/lon plus the human-readable area. Neighborhood precision only. */
    public record GeoLocationView(
            BigDecimal latitude,
            BigDecimal longitude,
            String address,
            String city,
            String district
    ) {
    }

    public record RequesterView(
            UUID id,
            String name,
            String bloodGroup
    ) {
    }

    public static BloodRequestViewDto from(BloodRequest request) {
        var geo = request.getGeoLocation();
        var owner = request.getRequester();
        String ownerName = owner == null ? null
                : ((owner.getFirstName() != null ? owner.getFirstName() : "")
                + " " + (owner.getLastName() != null ? owner.getLastName() : "")).trim();
        return new BloodRequestViewDto(
                request.getId(),
                request.getStatus() != null ? request.getStatus().name() : null,
                request.getBloodGroup() != null ? request.getBloodGroup().name() : null,
                request.getRequiredQuantity(),
                request.getUrgency() != null ? request.getUrgency().name() : null,
                request.getMessage(),
                geo == null ? null : new GeoLocationView(
                        geo.getLatitude(), geo.getLongitude(),
                        geo.getAddress(), geo.getCity(), geo.getDistrict()),
                owner == null ? null : new RequesterView(
                        owner.getId(), ownerName,
                        owner.getBloodGroup() != null ? owner.getBloodGroup().name() : null),
                request.getAcceptedDonor() != null ? request.getAcceptedDonor().getId() : null,
                request.getRequestTime(),
                request.getExpiresAt(),
                request.getStatus() != null
                        && request.getStatus().allowsChat()
                        && request.getAcceptedDonor() != null
        );
    }
}
