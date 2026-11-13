package com.bd.blooddonorfinder.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ResendOtpRequest {

    @NotNull(message = "user_id is required")
    @JsonProperty("user_id")
    UUID userId;

    @NotNull(message = "purpose is required")
    String purpose;
}
