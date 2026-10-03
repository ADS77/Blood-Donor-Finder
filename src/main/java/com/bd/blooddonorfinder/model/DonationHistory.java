package com.bd.blooddonorfinder.model;

import com.bd.blooddonorfinder.model.common.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "donation_history",
        indexes = {
                @Index(name = "idx_donation_history_donor", columnList = "donor_id"),
                @Index(name = "idx_donation_history_recipient", columnList = "recipient_id"),
                @Index(name = "idx_donation_history_request", columnList = "request_id"),
                @Index(name = "idx_donation_history_date", columnList = "donation_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class DonationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "donor_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_donation_history_donor")
    )
    private User donor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "recipient_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_donation_history_recipient")
    )
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "request_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_donation_history_request")
    )
    private BloodRequest request;

    @Column(name = "donation_date", nullable = false)
    private LocalDateTime donationDate;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private Boolean verified = false;

}
