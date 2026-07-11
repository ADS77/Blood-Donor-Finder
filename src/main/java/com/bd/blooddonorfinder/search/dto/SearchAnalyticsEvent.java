package com.bd.blooddonorfinder.search.dto;

import java.time.Instant;
import java.util.UUID;

public record SearchAnalyticsEvent(
        UUID searchId,
        String bloodGroup,
        double lat,
        double lng,
        int radiusKm,
        int resultCount,
        String searchPath,
        long latencyMs,
        boolean expanded,
        String urgency,
        Instant timestamp
) {
    public static SearchAnalyticsEvent from(SearchRequest req, SearchResponse resp, long latencyMs) {
        return new SearchAnalyticsEvent(
                UUID.randomUUID(),
                req.bloodGroup(),
                Math.round(req.lat()  * 100.0) / 100.0,
                Math.round(req.lng()  * 100.0) / 100.0,
                resp.metaData().radiusKm(),
                resp.metaData().total(),
                resp.metaData().searchPath(),
                latencyMs,
                resp.metaData().expanded(),
                req.urgency().name(),
                Instant.now()
        );
    }
}
