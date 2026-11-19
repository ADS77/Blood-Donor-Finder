package com.bd.blooddonorfinder.payload.response;

import com.bd.blooddonorfinder.model.common.GeoLocation;
import com.bd.blooddonorfinder.model.common.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Public view of one donor, safe to show to any authenticated user.
 *
 * <p>Contact details (email/phone) and exact coordinates are deliberately
 * absent: donors are contacted through chat once a request is accepted, and
 * location precision stays at the neighbourhood level. The caller's own full
 * profile is served by {@code GET /api/user/me}.
 *
 * @param memberSince account creation time, useful as a trust signal
 */
public record DonorDetailDto(
        UUID id,
        String name,
        String bloodGroup,
        Boolean isVerified,
        Boolean isAvailable,
        LocalDate lastDonationDate,
        Double rating,
        Long ratingCount,
        Long totalDonations,
        String city,
        String district,
        String country,
        String imageUrl,
        LocalDateTime memberSince
) {

    public static DonorDetailDto from(User user) {
        GeoLocation geo = user.getGeoLocation();
        String first = user.getFirstName() != null ? user.getFirstName() : "";
        String last = user.getLastName() != null ? user.getLastName() : "";
        String name = (first + " " + last).trim();
        return new DonorDetailDto(
                user.getId(),
                name.isEmpty() ? "Unknown" : name,
                user.getBloodGroup() != null ? user.getBloodGroup().name() : null,
                user.getIsVerified(),
                user.getIsAvailable(),
                user.getLastDonationDate() != null ? user.getLastDonationDate().toLocalDate() : null,
                user.getRating(),
                user.getRatingCount(),
                user.getTotalDonations(),
                geo != null ? geo.getCity() : null,
                geo != null ? geo.getDistrict() : null,
                geo != null ? geo.getCountry() : null,
                user.getImageUrl(),
                user.getCreatedAt()
        );
    }
}
