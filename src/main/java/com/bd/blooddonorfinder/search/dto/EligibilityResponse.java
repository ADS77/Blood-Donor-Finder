package com.bd.blooddonorfinder.search.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Response from the Eligibility Service")
public record EligibilityResponse(

        UUID donorId,
        boolean isEligible,
        Instant eligibleSince
) {}
