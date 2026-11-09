package com.bd.blooddonerfinder.payload.request;

import com.bd.blooddonerfinder.model.GeoLocation;
import com.bd.blooddonerfinder.model.enums.BloodGroup;
import lombok.Data;

import java.io.Serializable;

@Data
public class BloodRequestDto implements Serializable {
    private long userId;
    private BloodGroup neededBloodGroup;
    private int quantity;
    private GeoLocation geoLocation;
    private String message;
}
