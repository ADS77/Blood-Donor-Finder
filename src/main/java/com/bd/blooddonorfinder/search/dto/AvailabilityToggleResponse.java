package com.bd.blooddonorfinder.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;
@Schema(description = "Result of an availability toggle operation")
public record AvailabilityToggleResponse(
        @Schema(description = "The donor's UUID")
        UUID donorId,

        @Schema(description = "New availability state")
        boolean isAvailable,

        @Schema(description = "True if the donor was added to the Redis geo index")
        boolean geoIndexed
) {

}
