package com.bd.blooddonorfinder.payload.request;

import com.bd.blooddonorfinder.model.common.GeoLocation;
import com.bd.blooddonorfinder.model.enums.BloodGroup;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Self-service profile update payload.
 *
 * <p>Only fields a donor is allowed to change are exposed. Identity,
 * credentials, roles and verification flags are intentionally absent so a
 * client cannot escalate privileges or overwrite another account by mass
 * assignment.</p>
 */
@Data
public class UpdateProfileRequest implements Serializable {

    @Size(max = 100, message = "First name must not exceed 100 characters")
    @JsonProperty("first_name")
    private String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    @JsonProperty("last_name")
    private String lastName;

    private BloodGroup bloodGroup;

    @JsonProperty("is_available")
    private Boolean isAvailable;

    @Valid
    private GeoLocation geoLocation;

    @JsonProperty("last_donation_date")
    private LocalDateTime lastDonationDate;
}
