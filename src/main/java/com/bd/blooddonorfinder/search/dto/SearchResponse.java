package com.bd.blooddonorfinder.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Donor search response")
public record SearchResponse(
        @Schema(description = "Ranked donor list — max 20 results")
        List<DonorResult> donors,
        @Schema(description = "Search metadata and operational diagnostics")
        SearchMeta metaData
) {
    public static SearchResponse empty(SearchMeta meta) {
        return new SearchResponse(List.of(), meta);
    }
}
