package com.bd.blooddonorfinder.model.es.SearchParam;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = true)
public class GeoDonorSearchParams extends UserSearchParams implements Serializable {

    private Double latitude;
    private Double longitude;
    private Double radiusKm;

    /**
     * Location freshness window in days: donors whose coordinates haven't been
     * confirmed within this window are excluded from the search (their position
     * may no longer be where they live). Documents missing the freshness stamp
     * remain searchable so legacy data is not silently dropped.
     */
    private Long maxLocationAgeDays;

    public static GeoDonorSearchParams of(UserSearchParams base, double latitude, double longitude, double radiusKm) {
        return of(base, latitude, longitude, radiusKm, null);
    }

    public static GeoDonorSearchParams of(UserSearchParams base, double latitude, double longitude,
                                          double radiusKm, Long maxLocationAgeDays) {
        GeoDonorSearchParams params = new GeoDonorSearchParams();
        if (base != null) {
            params.setQueryModel(base.getQueryModel());
            params.setLength(base.getLength());
            params.setSearchAfter(base.getSearchAfter());
        }
        params.setLatitude(latitude);
        params.setLongitude(longitude);
        params.setRadiusKm(radiusKm);
        params.setMaxLocationAgeDays(maxLocationAgeDays);
        return params;
    }
}
