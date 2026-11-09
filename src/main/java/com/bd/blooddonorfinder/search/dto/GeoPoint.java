package com.bd.blooddonorfinder.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Geographic coordinate pair")
public record GeoPoint (
    @NotNull
    @DecimalMin("-90.0") @DecimalMax("90.0")
    @Schema(description = "Latitude in decimal degrees")
    Double lat,

    @NotNull
    @DecimalMin("-180.0") @DecimalMax("180.0")
    @Schema(description = "Longitude in decimal degrees")
    Double lng
){}
