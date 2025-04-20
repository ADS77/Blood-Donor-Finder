package com.bd.blooddonerfinder.model;

import com.bd.blooddonerfinder.model.enums.BloodGroup;
import com.bd.blooddonerfinder.model.enums.Role;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue
    private Long id;
    private String name;
    private String email;
    private String phone;
    @Enumerated(EnumType.STRING)
    private BloodGroup bloodGroup;
    @Enumerated(EnumType.STRING)
    private Role role;
    private Boolean isVerified;
    private Boolean isAvailable;
    private LocalDate lastDonationDate;

    @Embedded
    private Location location;

    private LocalDateTime createdAt;

    public User(String name, String email, String phone, BloodGroup bloodGroup, Role role, Location location) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.bloodGroup = bloodGroup;
        this.role = role;
        this.location = location;
    }

}
