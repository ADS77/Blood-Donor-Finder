package com.bd.blooddonerfinder.model;

import java.io.Serializable;

public class GeoResponse implements Serializable {
    private String latitude;
    private String longitude;
    private String displayName;

    public GeoResponse(String lat, String lon, String display_name) {
        this.latitude = lat;
        this.longitude = lon;
        this.displayName = display_name;
    }

    public String getLatitude() {
        return latitude;
    }

    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }

    public String getLongitude() {
        return longitude;
    }

    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
