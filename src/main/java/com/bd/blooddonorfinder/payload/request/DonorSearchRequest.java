package com.bd.blooddonorfinder.payload.request;

import com.bd.blooddonorfinder.model.common.GeoLocation;
import com.bd.blooddonorfinder.model.enums.BloodGroup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

@Data
@NoArgsConstructor
@ToString
@EqualsAndHashCode
public class DonorSearchRequest implements Serializable {

    @Valid
    @NotNull(message = "Geo location is required")
    private GeoLocation geoLocation;

    @NotNull(message = "Blood group is required")
    private BloodGroup bloodGroup;

    @DecimalMin(value = "0.1", message = "Radius must be positive")
    @DecimalMax(value = "500.0", message = "Radius must not exceed 500 km")
    private Double radius;

    @Email(message = "Receiver email must be valid")
    @Size(max = 255)
    private String receiverEmail;

    @Pattern(regexp = "^\\+?[0-9]{6,15}$", message = "Receiver phone must be a valid phone number")
    private String receiverPhone;

    @Pattern(regexp = "^(LOW|MEDIUM|HIGH|CRITICAL)$", message = "Emergency level must be LOW, MEDIUM, HIGH or CRITICAL")
    private String emergencyLevel;

    @Size(max = 1000, message = "Request description must not exceed 1000 characters")
    private String requestDescription;

    @Size(max = 200, message = "Hospital name must not exceed 200 characters")
    private String hospitalName;
}
