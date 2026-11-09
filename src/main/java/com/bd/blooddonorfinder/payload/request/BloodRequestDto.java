package com.bd.blooddonorfinder.payload.request;

import com.bd.blooddonorfinder.model.common.GeoLocation;
import com.bd.blooddonorfinder.model.enums.BloodGroup;
import lombok.Data;

import java.io.Serializable;
import java.util.UUID;

@Data
public class BloodRequestDto implements Serializable {
    private UUID userId;
    private BloodGroup neededBloodGroup;
    private int quantity;
    private GeoLocation geoLocation;
    private String message;
}
