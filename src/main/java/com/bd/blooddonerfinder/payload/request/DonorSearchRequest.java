package com.bd.blooddonerfinder.payload.request;

import com.bd.blooddonerfinder.model.enums.BloodGroup;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
@Data
public class DonorSearchRequest {
    private double latitude;
    private double longitude;
    private BloodGroup bloodGroup;
    private double radius;
}
