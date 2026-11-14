package com.bd.blooddonorfinder.payload.response;

import com.bd.blooddonorfinder.model.es.documents.DonorSearchDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NearbyDonorSearchResponse implements Serializable {

    /** Donors on the current page */
    private List<DonorSearchDocument> donors;

    /** Total donors found within the final radius */
    private long totalHits;

    /** Elasticsearch query time in millis */
    private long timeMs;

    /** Search center point */
    private double latitude;
    private double longitude;

    /** Radius (km) at which donors were found */
    private double searchRadiusKm;

    /** How many times the radius was expanded (0 = found within initial radius) */
    private int expansions;

    /** Cursor for the next page, null when no more pages */
    private Object[] searchAfter;
}
