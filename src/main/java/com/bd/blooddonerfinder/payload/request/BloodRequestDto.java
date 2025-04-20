package com.bd.blooddonerfinder.payload.request;

import com.bd.blooddonerfinder.model.Location;
import com.bd.blooddonerfinder.model.enums.BloodGroup;
import lombok.Data;

import java.io.Serializable;

@Data
public class BloodRequestDto implements Serializable {
    private Long userId;
    private BloodGroup neededBloodGroup;
    private int quantity;
    private Location location;
    private String message;
}
