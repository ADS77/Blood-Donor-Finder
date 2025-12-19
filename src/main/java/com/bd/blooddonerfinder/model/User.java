package com.bd.blooddonerfinder.model;

import com.bd.blooddonerfinder.model.enums.BloodGroup;
import com.bd.blooddonerfinder.model.enums.Role;
import com.fasterxml.jackson.annotation.JsonFormat;
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    @Column(unique = true, nullable = false)
    private String email;
    @Column(unique = true, nullable = false)
    private String phone;
    @Column(name = "blood_group")
    @Enumerated(EnumType.STRING)
    private BloodGroup bloodGroup;
    @Enumerated(EnumType.STRING)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "role")
    private Role role;
    @Column(name = "verified")
    private Boolean isVerified;
    @Column(name = "is_available")
    private Boolean isAvailable;
    @Column(name = "last_donation_date")
    private LocalDateTime lastDonationDate;
    @Column(columnDefinition = "DOUBLE DEFAULT 0.0")
    private Double rating = 0.0;
    @Column(columnDefinition = "BIGINT DEFAULT 0")
    private Long totalDonations = 0L;
    @Column(name = "image_url")
    private String imageUrl;
    @Embedded
    private GeoLocation geoLocation;
    @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public User(String name, String email, String phone, BloodGroup bloodGroup, Role role, GeoLocation geoLocation) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.bloodGroup = bloodGroup;
        this.role = role;
        this.geoLocation = geoLocation;
        this.createdAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) && Objects.equals(name, user.name) && Objects.equals(email, user.email) && Objects.equals(phone, user.phone) && bloodGroup == user.bloodGroup && role == user.role && Objects.equals(isVerified, user.isVerified) && Objects.equals(isAvailable, user.isAvailable) && Objects.equals(lastDonationDate, user.lastDonationDate) && Objects.equals(geoLocation, user.geoLocation) && Objects.equals(createdAt, user.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, email, phone, bloodGroup, role, isVerified, isAvailable, lastDonationDate, geoLocation, createdAt);
    }
}
