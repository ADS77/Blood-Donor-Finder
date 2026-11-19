package com.bd.blooddonorfinder.payload.response;

import com.bd.blooddonorfinder.model.common.GeoLocation;
import com.bd.blooddonorfinder.model.common.Role;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.GeoStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Full profile of the authenticated caller. Unlike the donor-facing views this
 * includes the caller's own contact details, so it must only ever be built for
 * the principal it describes — never for another user and never for the donor
 * search surface.
 *
 * @param email      decrypted from the PII token; null when unavailable
 * @param phone      decrypted from the PII token; null when unavailable
 * @param memberSince account creation time, used for "member since" badges
 */
public record UserProfileDto(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String bloodGroup,
        List<String> roles,
        Boolean isVerified,
        Boolean isAvailable,
        Double rating,
        Long ratingCount,
        Long totalDonations,
        LocalDateTime lastDonationDate,
        String imageUrl,
        LocalDateTime memberSince,
        LocationView location
) {

    /** The caller's own location, including exact coordinates. */
    public record LocationView(
            String address,
            String city,
            String district,
            String country,
            String zipcode,
            BigDecimal latitude,
            BigDecimal longitude,
            String geoStatus,
            Instant geoUpdatedAt
    ) {
        static LocationView from(GeoLocation geo) {
            if (geo == null) {
                return null;
            }
            GeoStatus status = geo.getGeoStatus();
            return new LocationView(
                    geo.getAddress(),
                    geo.getCity(),
                    geo.getDistrict(),
                    geo.getCountry(),
                    geo.getZipcode(),
                    geo.getLatitude(),
                    geo.getLongitude(),
                    status != null ? status.name() : null,
                    geo.getGeoUpdatedAt()
            );
        }
    }

    public static UserProfileDto from(User user, String email, String phone) {
        return new UserProfileDto(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                email,
                phone,
                user.getBloodGroup() != null ? user.getBloodGroup().name() : null,
                user.getRoles() == null ? List.of() : user.getRoles().stream()
                        .map(Role::getName)
                        .sorted(Comparator.naturalOrder())
                        .toList(),
                user.getIsVerified(),
                user.getIsAvailable(),
                user.getRating(),
                user.getRatingCount(),
                user.getTotalDonations(),
                user.getLastDonationDate(),
                user.getImageUrl(),
                user.getCreatedAt(),
                LocationView.from(user.getGeoLocation())
        );
    }
}
