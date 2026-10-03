package com.bd.blooddonorfinder.payload.request;

import com.bd.blooddonorfinder.model.common.GeoLocation;
import com.bd.blooddonorfinder.model.enums.BloodGroup;
import com.bd.blooddonorfinder.validator.ValidPassword;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import javax.annotation.Nullable;
import java.io.Serializable;

@Data
public class RegisterRequest implements Serializable {

    @NotBlank(message = "First name is required")
    @Size(max = 100)
    @JsonProperty("first_name")
    String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100)
    @JsonProperty("last_name")
    String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Must be a valid phone number")
    private String phone;

    @NotBlank(message = "Password is required")
    @ValidPassword
    private String password;

    @NotNull(message = "Blood group is required")
    @JsonProperty("blood_group")
    private BloodGroup bloodGroup;

    @Valid
    @Nullable()
    @JsonProperty("geo_location")
    private GeoLocation geoLocation;

    private String role;
}
