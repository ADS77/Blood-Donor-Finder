package com.bd.blooddonorfinder.payload.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class NearbyDonorSearchRequest implements Serializable {

    @NotNull
    @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
    private Double latitude;

    @NotNull
    @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
    private Double longitude;

    /** Optional filter, e.g. "A_POSITIVE" */
    private String bloodGroup;

    /** Optional filter */
    private Boolean isVerified;

    /** Optional filter */
    private Boolean isAvailable;

    /** Search starts with this radius. Default 5 km */
    @DecimalMin(value = "0.1", message = "initialRadiusKm must be at least 0.1")
    private Double initialRadiusKm;

    /**
     * Radius grows by this step on every miss while below 25 km; above 25 km the
     * step widens to 10 km. Default walk:
     * 5 → 10 → 15 → 20 → 25 → 35 → 45 → 55 → 65 → 75 → 80 km (hard cap).
     */
    @DecimalMin(value = "0.1", message = "radiusStepKm must be at least 0.1")
    private Double radiusStepKm;

    /**
     * Upper bound of the expanding search. Default 80 km (the cap can't exceed 80).
     */
    @DecimalMin(value = "1.0", message = "maxRadiusKm must be at least 1 km")
    @DecimalMax(value = "80.0", message = "maxRadiusKm can't exceed 80 km")
    private Double maxRadiusKm;

    /**
     * Optional additional expansion bound after the initial miss (0 = search the
     * initial radius only). When omitted, expansion is governed solely by the
     * radius cap.
     */
    @Min(value = 0, message = "maxExpansions can't be negative")
    private Integer maxExpansions;

    /**
     * Location freshness window in days: donors whose coordinates haven't been
     * confirmed within this window are excluded (their stored position may be
     * obsolete). Default comes from app.search.max-location-age-days.
     */
    @Min(value = 1, message = "maxLocationAgeDays must be at least 1")
    @Max(value = 3650, message = "maxLocationAgeDays can't exceed 3650")
    private Integer maxLocationAgeDays;

    /** Page size. Default 10, max 100 */
    @Min(value = 1, message = "length must be at least 1")
    @Max(value = 100, message = "length can't exceed 100")
    private Integer length;

    /** Cursor from previous response to fetch the next page */
    private Object[] searchAfter;
}
