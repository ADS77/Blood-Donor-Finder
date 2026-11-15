package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.payload.request.LocationUpdateRequest;
import com.bd.blooddonorfinder.payload.response.LocationUpdateResponse;

import java.util.UUID;

/**
 * Ingests donor location updates from their device and keeps the DB/ES
 * coordinates fresh. This is the write side of the location-freshness pipeline.
 */
public interface LocationIngestService {

    /**
     * Updates the authenticated donor's coordinates.
     * Throttled per user and deduplicated for micro-movements.
     */
    LocationUpdateResponse updateLocation(UUID userId, LocationUpdateRequest request);

    /**
     * Publishes the location-changed event for a position that was already
     * persisted by another code path (e.g. profile update) so Elasticsearch
     * stays in sync. Does not throttle or dedupe — the caller decides.
     */
    void propagateLocationChange(User user);
}
