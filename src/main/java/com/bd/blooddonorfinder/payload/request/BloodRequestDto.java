package com.bd.blooddonorfinder.payload.request;

import com.bd.blooddonorfinder.model.common.GeoLocation;
import com.bd.blooddonorfinder.model.enums.BloodGroup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * Payload for creating a blood request.
 *
 * <p>There is deliberately no {@code userId} field: the requester is always the
 * authenticated principal. Accepting a client-supplied user id allowed one
 * account to create requests on behalf of another (IDOR).</p>
 */
@Data
public class BloodRequestDto implements Serializable {

    @NotNull(message = "Needed blood group is required")
    private BloodGroup neededBloodGroup;

    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 20, message = "Quantity must not exceed 20")
    private int quantity;

    @Valid
    @NotNull(message = "Geo location is required")
    private GeoLocation geoLocation;

    @Size(max = 1000, message = "Message must not exceed 1000 characters")
    private String message;
}
