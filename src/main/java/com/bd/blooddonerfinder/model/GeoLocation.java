package com.bd.blooddonerfinder.model;

import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
public class GeoLocation implements Serializable {
    private String address;
    private String city;
    private String district;
    private Double latitude;
    private Double longitude;
    private String zipcode;

}
