package com.bd.blooddonerfinder.payload.request;

import com.bd.blooddonerfinder.model.Location;
import com.bd.blooddonerfinder.model.enums.BloodGroup;
import lombok.Data;
@Data
public class DonorSearchRequest {
    private Location location;
    private BloodGroup bloodGroup;
    private double radius;
    private  String receiverEmail;
    private String receiverPhone;
}
