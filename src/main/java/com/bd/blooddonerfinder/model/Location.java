package com.bd.blooddonerfinder.model;

import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Embeddable
@Data
public class Location implements Serializable {
    private String address;
    private String city;
    private String district;
    private Double latitude;
    private Double longitude;

}
