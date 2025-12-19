package com.bd.blooddonerfinder.model;

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

    @ManyToOne
    private User donor;

    @ManyToOne
    private User recipient;

    @ManyToOne
    private BloodRequest request;

    private LocalDateTime donationDate;
    private String notes;

    private Boolean verified;

}
