package com.bd.blooddonorfinder.payload.request;

import com.bd.blooddonorfinder.model.GeoLocation;
import com.bd.blooddonorfinder.model.enums.BloodGroup;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

@Data
@NoArgsConstructor
@ToString
@EqualsAndHashCode
public class DonorSearchRequest implements Serializable {
    private GeoLocation geoLocation;
    private BloodGroup bloodGroup;
    private Double radius;
    private String receiverEmail;
    private String receiverPhone;
    private String emergencyLevel;
    private String requestDescription;
    private String hospitalName;
}
