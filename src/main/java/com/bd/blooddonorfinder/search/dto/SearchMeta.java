package com.bd.blooddonorfinder.search.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Search metadata — operational diagnostics and expansion info")
public record SearchMeta(
        @Schema(description = "Total matching donors returned")
        int total,

        @Schema(description = "Actual radius used (may be expanded)", example = "25")
        int radiusKm,

        @Schema(description = "True if radius was auto-expanded from the initial value")
        boolean expanded,

        @Schema(description = "Which search layer served this result")
        String searchPath,

        @Schema(description = "End-to-end latency in milliseconds")
        long latencyMs,

        @Schema(description = "True when zero donors found at any radius")
        boolean noDonorsFound,

        @Schema(description = "True when /donors/rare searched nationally (no radius)")
        Boolean nationalSearch,

        @Schema(description = "True when compatible blood group donors are included")
        Boolean compatibilityFallback
) {
        public static Builder builder() { return new Builder(); }

        public static final class Builder {
                private int total;
                private int radiusKm;
                private boolean expanded;
                private String searchPath;
                private long latencyMs;
                private boolean noDonorsFound;
                private Boolean nationalSearch;
                private Boolean compatibilityFallback;

                public Builder total(int v)                    { total = v; return this; }
                public Builder radiusKm(int v)                 { radiusKm = v; return this; }
                public Builder expanded(boolean v)             { expanded = v; return this; }
                public Builder searchPath(String v)            { searchPath = v; return this; }
                public Builder latencyMs(long v)               { latencyMs = v; return this; }
                public Builder noDonorsFound(boolean v)        { noDonorsFound = v; return this; }
                public Builder nationalSearch(Boolean v)       { nationalSearch = v; return this; }
                public Builder compatibilityFallback(Boolean v){ compatibilityFallback = v; return this; }

                public SearchMeta build() {
                        return new SearchMeta(total, radiusKm, expanded, searchPath,
                                latencyMs, noDonorsFound, nationalSearch, compatibilityFallback);
                }
        }
}

