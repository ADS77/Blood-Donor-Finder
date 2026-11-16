package com.bd.blooddonorfinder.payload.response;

import com.bd.blooddonorfinder.model.common.User;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Safe view of a donor for administrative search/notify endpoints. JPA
 * {@link User} entities are never serialized to clients — they carry token
 * references, credentials fields and lazy associations.
 *
 * @param name display name only; contact details stay out by design
 *             (contact happens through the notify flow)
 */
public record DonorSummaryDto(
        UUID id,
        String name,
        String bloodGroup,
        Boolean isVerified,
        Boolean isAvailable,
        LocalDate lastDonationDate,
        Double rating,
        Long totalDonations,
        String city,
        String district
) {

    public static DonorSummaryDto from(User user) {
        var geo = user.getGeoLocation();
        return new DonorSummaryDto(
                user.getId(),
                user.getFirstName() != null ? user.getFirstName() : "",
                user.getBloodGroup() != null ? user.getBloodGroup().name() : null,
                user.getIsVerified(),
                user.getIsAvailable(),
                user.getLastDonationDate() != null ? user.getLastDonationDate().toLocalDate() : null,
                user.getRating(),
                user.getTotalDonations(),
                geo != null ? geo.getCity() : null,
                geo != null ? geo.getDistrict() : null
        );
    }

    public static List<DonorSummaryDto> fromAll(List<User> users) {
        return users.stream().map(DonorSummaryDto::from).toList();
    }
}
