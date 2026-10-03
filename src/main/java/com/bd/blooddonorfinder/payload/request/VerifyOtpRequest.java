package com.bd.blooddonorfinder.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
@Getter
@Setter
@Builder
public class VerifyOtpRequest {
        @NotNull(message = "user_id is required")
        @JsonProperty("user_id")
        UUID userId;

        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
        String otp;

        @NotNull(message = "purpose is required")
        String purpose;
}
