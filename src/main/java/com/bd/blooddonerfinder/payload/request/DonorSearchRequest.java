package com.bd.blooddonerfinder.payload.request;

import com.bd.blooddonerfinder.model.Location;
import com.bd.blooddonerfinder.model.enums.BloodGroup;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@ToString
@EqualsAndHashCode
public class DonorSearchRequest {
    private Location location;
    private BloodGroup bloodGroup;
    private double radius;
    private  String receiverEmail;
    private String receiverPhone;
    private String emergencyLevel;
    private String requestDescription;
    private String hospitalName;
}
