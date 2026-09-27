package com.bd.blooddonorfinder.model.common;

import com.bd.blooddonorfinder.model.PiiToken;
import com.bd.blooddonorfinder.model.enums.BloodGroup;
import com.bd.blooddonorfinder.payload.request.RegisterRequest;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "phone_token_id",
            foreignKey = @ForeignKey(name = "fk_app_user_phone_token")
    )
    private PiiToken phoneToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "email_token_id",
            foreignKey = @ForeignKey(name = "fk_app_user_email_token")
    )
    private PiiToken emailToken;

    @Column(name = "password", nullable = false)
    @JsonIgnore
    private String password;

    @Column(name = "blood_group")
    @Enumerated(EnumType.STRING)
    private BloodGroup bloodGroup;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @Column(name = "verified")
    private Boolean isVerified;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "failed_otp_attempts", nullable = false)
    private int failedOtpAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

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

    @Version
    @Column(name = "version")
    private Long version;

    @Column(nullable = false)
    private boolean enabled = true;

    public User(String firstName, String lastName, BloodGroup bloodGroup) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.bloodGroup = bloodGroup;
        this.enabled = true;
    }
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }


    public static User of(RegisterRequest registerRequest, String encodedPass, PiiToken phoneToken, PiiToken emailToken) {
        User user = new User(
                registerRequest.getFirstName(),
                registerRequest.getLastName(),
                registerRequest.getBloodGroup());
        user.setGeoLocation(registerRequest.getGeoLocation() != null ? registerRequest.getGeoLocation() : null);
        user.setPassword(encodedPass);
        user.setPhoneToken(phoneToken);
        user.setEmailToken(emailToken);
        user.setIsVerified(false);
        user.setActive(true);
        user.setIsAvailable(true);
        user.setVersion(1L);
        return user;
    }


    public void incrementFailedOtpAttempts() {
        this.failedOtpAttempts++;
    }

    public void resetFailedOtpAttempts() {
        this.failedOtpAttempts = 0;
    }

    public void lockUntil(Instant until) {
        this.lockedUntil = until;
    }

    public boolean isLocked() {
        return lockedUntil != null && Instant.now().isBefore(lockedUntil);
    }

    public void markVerified() {
        this.isVerified = true;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public void removeRole(Role role) {
        this.roles.remove(role);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return isActive() == user.isActive() && getFailedOtpAttempts() == user.getFailedOtpAttempts() && isEnabled() == user.isEnabled() && getId().equals(user.getId()) && getFirstName().equals(user.getFirstName()) && Objects.equals(getLastName(), user.getLastName()) && getPhoneToken().equals(user.getPhoneToken()) && getEmailToken().equals(user.getEmailToken()) && getPassword().equals(user.getPassword()) && getBloodGroup() == user.getBloodGroup() && getRoles().equals(user.getRoles()) && getIsVerified().equals(user.getIsVerified()) && getLockedUntil().equals(user.getLockedUntil()) && getIsAvailable().equals(user.getIsAvailable()) && Objects.equals(getLastDonationDate(), user.getLastDonationDate()) && getRating().equals(user.getRating()) && getTotalDonations().equals(user.getTotalDonations()) && Objects.equals(getImageUrl(), user.getImageUrl()) && Objects.equals(getGeoLocation(), user.getGeoLocation()) && getCreatedAt().equals(user.getCreatedAt()) && getUpdatedAt().equals(user.getUpdatedAt()) && getVersion().equals(user.getVersion());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getFirstName(), getLastName(), getPhoneToken(), getEmailToken(), getPassword(), getBloodGroup(), getRoles(), getIsVerified(), isActive(), getFailedOtpAttempts(), getLockedUntil(), getIsAvailable(), getLastDonationDate(), getRating(), getTotalDonations(), getImageUrl(), getGeoLocation(), getCreatedAt(), getUpdatedAt(), getVersion(), isEnabled());
    }
}
