package com.bd.blooddonerfinder.model;

import com.bd.blooddonerfinder.model.enums.BloodGroup;
import com.bd.blooddonerfinder.model.enums.Role;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Data
@NoArgsConstructor
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue
    private Long id;
    private String name;
    @Column(unique = true, nullable = false)
    private String email;
    @Column(unique = true, nullable = false)
    private String phone;
    @Enumerated(EnumType.STRING)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private BloodGroup bloodGroup;
    @Enumerated(EnumType.STRING)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Role role;
    private Boolean isVerified;
    private Boolean isAvailable;
    private LocalDate lastDonationDate;

    @Embedded
    private Location location;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public User(String name, String email, String phone, BloodGroup bloodGroup, Role role, Location location) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.bloodGroup = bloodGroup;
        this.role = role;
        this.location = location;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) && Objects.equals(name, user.name) && Objects.equals(email, user.email) && Objects.equals(phone, user.phone) && bloodGroup == user.bloodGroup && role == user.role && Objects.equals(isVerified, user.isVerified) && Objects.equals(isAvailable, user.isAvailable) && Objects.equals(lastDonationDate, user.lastDonationDate) && Objects.equals(location, user.location) && Objects.equals(createdAt, user.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, email, phone, bloodGroup, role, isVerified, isAvailable, lastDonationDate, location, createdAt);
    }
}
