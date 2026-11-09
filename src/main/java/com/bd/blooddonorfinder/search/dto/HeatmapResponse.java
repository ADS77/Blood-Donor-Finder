package com.bd.blooddonorfinder.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Donor density heatmap data for the map dashboard")
public record HeatmapResponse(

        @Schema(description = "Grid cells with donor counts")
        List<HeatmapCell> cells
) {
    @Schema(description = "A single grid cell in the heatmap")
    public record HeatmapCell(
            @Schema(example = "23.71") double lat,
            @Schema(example = "90.31") double lng,
            @Schema(example = "4")    long count
    ) {}
}