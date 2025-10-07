package com.bd.blooddonerfinder.payload.request;

import com.bd.blooddonerfinder.model.GeoLocation;
import com.bd.blooddonerfinder.model.enums.BloodGroup;
import com.bd.blooddonerfinder.model.enums.Role;
import lombok.Data;

import java.io.Serializable;

@Data
public class UserRegistrationRequest implements Serializable {

    private String name;
    private String email;
    private String phone;
    private Role role;
    private BloodGroup bloodGroup;
    private GeoLocation geoLocation;
}
