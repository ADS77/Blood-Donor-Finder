package com.bd.blooddonorfinder.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request to toggle donor availability")
public record AvailabilityToggleRequest(

        @NotNull
        @Schema(description = "Desired availability state")
        Boolean isAvailable,

        @Valid
        @Schema(description = "Current location — required when setting available=true")
        com.bd.blooddonorfinder.search.dto.GeoPoint location
) {}