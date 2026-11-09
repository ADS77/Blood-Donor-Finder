package com.bd.blooddonorfinder.model;

import com.bd.blooddonorfinder.model.enums.BloodGroup;
import com.bd.blooddonorfinder.model.enums.GeoStatus;
import com.bd.blooddonorfinder.model.enums.Role;
import com.bd.blooddonorfinder.payload.request.UserRegistrationRequest;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Data
@NoArgsConstructor
@Table(name = "app_user")
@EqualsAndHashCode
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password", nullable = false)
    @JsonIgnore
    private String password;

    @Column(unique = true, nullable = false)
    private String phone;

    @Column(name = "blood_group")
    @Enumerated(EnumType.STRING)
    private BloodGroup bloodGroup;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

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
    @AttributeOverrides({
            @AttributeOverride(name = "geoStatus", column = @Column(name = "geo_status")),
            @AttributeOverride(name = "geoRetryCount", column = @Column(name = "geo_retry_count")),
            @AttributeOverride(name = "geoLastError", column = @Column(name = "geo_last_error"))
    })
    private GeoLocation geoLocation;

    @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    public User(String name, String email, String phone, BloodGroup bloodGroup, Set<Role> roles, GeoLocation geoLocation) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.bloodGroup = bloodGroup;
        this.roles = roles;
        this.geoLocation = geoLocation;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }


    public static User of (UserRegistrationRequest registrationRequest, String encodedPassword){

        User newUser = new User(
                registrationRequest.getName(),
                registrationRequest.getEmail(),
                registrationRequest.getPhone(),
                registrationRequest.getBloodGroup(),
                //To-DO : currently treating all user as donor
                Collections.singleton(Role.DONOR),
                registrationRequest.getGeoLocation()
        );
        newUser.setIsVerified(false);
        newUser.setIsAvailable(true);
        newUser.setCreatedAt(LocalDateTime.now());
        newUser.setPassword(encodedPassword);
        newUser.setIsVerified(false);
        newUser.setIsAvailable(true);
        return newUser;
    }

}
