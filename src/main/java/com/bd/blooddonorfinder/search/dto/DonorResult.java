package com.bd.blooddonorfinder.search.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A single donor result- no raw contact details exposed")
public record DonorResult(
        @Schema(description = "Donor UUID")
        UUID id,

        @Schema(description = "Donor's blood group", example = "O-")
        String bloodGroup,

        @Schema(description = "Haversine distance from requester in km", example = "2.3")
        Double distanceKm,

        @Schema(description = "Trust score 0.0–5.0", example = "4.7")
        Double trustScore,

        @Schema(description = "Total lifetime donations", example = "12")
        Integer totalDonations,

        @Schema(description = "Whether this donor is platform-verified")
        Boolean isVerified,

        @Schema(description = "Last time the donor was active on the platform")
        Instant lastActiveAt,

        @Schema(description = "Proxy UUID — resolve via Contact Service for actual contact details")
        UUID maskedContactId,

        @Schema(description = "Computed ranking score — higher is better")
        Double rankingScore,

        //Compatibility fallback fields (non-null only when meta.compatibilityFallback=true)
        @Schema(description = "Only present when this is a compatible-group (not exact) match")
        String compatibilityNote
) {
    public DonorResult withScore(double score) {
        return new DonorResult(id, bloodGroup, distanceKm, trustScore, totalDonations,
                isVerified, lastActiveAt, maskedContactId, score, compatibilityNote);
    }
}
