package com.bd.blooddonorfinder.search.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.UUID;
@Schema(description = "Donor search query parameters")
public record SearchRequest(
        @NotBlank
        @Schema(description = "Blood group (normalized before use)", example = "O-")
        String bloodGroup,

        @NotNull
        @DecimalMin("20.5") @DecimalMax("26.6")
        @Schema(description = "Requester latitude — must be within Bangladesh bbox", example = "23.8103")
        Double lat,

        @NotNull
        @DecimalMin("88.0") @DecimalMax("92.7")
        @Schema(description = "Requester longitude — must be within Bangladesh bbox", example = "90.4125")
        Double lng,

        @Min(1) @Max(50)
        @Schema(description = "Search radius in km (1–50). Defaults to 10.", defaultValue = "10")
        Integer radiusKm,

        @Schema(description = "Search urgency — drives hot-path routing", example = "EMERGENCY",
                allowableValues = {"EMERGENCY", "URGENT", "STANDARD"})
        UrgencyLevel urgency,

        @Schema(description = "Filter results to donors affiliated with this organization")
        UUID orgId,

        @Schema(description = "Return only verified donors", defaultValue = "false")
        Boolean verifiedOnly,

        @Schema(description = "Page number (reserved — always returns top-20)", defaultValue = "0")
        Integer page,

        @Schema(description = "X-Request-ID for distributed tracing — populated by filter, not caller")
        String requestId
) {
        public SearchRequest {
                radiusKm = radiusKm != null ? radiusKm : 10;
                urgency = urgency != null ? urgency : UrgencyLevel.STANDARD;
                verifiedOnly = verifiedOnly != null ? verifiedOnly : false;
                page = page != null ? page       : 0;
        }

        public boolean isEmergency() {
                return urgency == UrgencyLevel.EMERGENCY || urgency == UrgencyLevel.URGENT;
        }

}
