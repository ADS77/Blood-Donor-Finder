package com.bd.blooddonorfinder.payload.request;

import com.bd.blooddonorfinder.model.GeoLocation;
import com.bd.blooddonorfinder.model.enums.BloodGroup;
import com.bd.blooddonorfinder.validator.ValidPassword;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.io.Serializable;

@Data
public class UserRegistrationRequest implements Serializable {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

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
    private BloodGroup bloodGroup;

    @Valid
    @NotNull(message = "Location information is required")
    private GeoLocation geoLocation;
}
