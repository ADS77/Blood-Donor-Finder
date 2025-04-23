package com.bd.blooddonerfinder.payload.request;

import com.bd.blooddonerfinder.model.Location;
import com.bd.blooddonerfinder.model.enums.BloodGroup;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Data
@Getter
@Setter
public class BloodRequestDto implements Serializable {
    private long userId;
    private BloodGroup neededBloodGroup;
    private int quantity;
    private Location location;
    private String message;
}
