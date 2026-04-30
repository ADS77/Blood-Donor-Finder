package com.bd.blooddonerfinder.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "donation_history")
public class DonationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donor_id", nullable = false)
    @JsonIgnoreProperties({"geoLocation", "createdAt", "updatedAt", "hibernateLazyInitializer"})
    private User donor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    @JsonIgnoreProperties({"geoLocation", "createdAt", "updatedAt", "hibernateLazyInitializer"})
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    @JsonIgnoreProperties({"geoLocation", "hibernateLazyInitializer"})
    private BloodRequest request;

    private LocalDateTime donationDate;
    private String notes;
    private Boolean verified;

}
