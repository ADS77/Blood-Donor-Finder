package com.bd.blooddonerfinder.model;

import java.io.Serializable;
import java.math.BigDecimal;

public class GeoResponse implements Serializable {
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String displayName;

    public GeoResponse(String lat, String lon, String display_name) {
        this.latitude = BigDecimal.valueOf(Double.valueOf(lat));
        this.longitude = BigDecimal.valueOf(Double.valueOf(lon));
        this.displayName = display_name;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
