package com.bd.blooddonorfinder.model;

import com.bd.blooddonorfinder.model.common.GeoLocation;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.BloodGroup;
import com.bd.blooddonorfinder.model.enums.RequestStatus;
import com.bd.blooddonorfinder.model.enums.RequestUrgency;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Data
@Table(name = "blood_request")
public class BloodRequest implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User requester;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group", nullable = false)
    private BloodGroup bloodGroup;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "request_time", nullable = false)
    private LocalDateTime requestTime;

    @Embedded
    private GeoLocation geoLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private RequestStatus status;

    @Column(name = "required_quantity")
    private Integer requiredQuantity;

    /** Donor who accepted this request — set atomically on first accept (chat gate). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_donor_id")
    private User acceptedDonor;

    private Instant acceptedAt;

    // ── donation completion flow (V38) ────────────────────────────────
    /** When the donor says the donation happened (set on mark-complete). */
    @Column(name = "donated_at")
    private Instant donatedAt;

    /** When the donor hit the complete endpoint. */
    @Column(name = "completion_requested_at")
    private Instant completionRequestedAt;

    @Column(name = "completion_notes", length = 500)
    private String completionNotes;

    /** When the requester approved the donation. */
    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "disputed_at")
    private Instant disputedAt;

    @Column(name = "dispute_reason", length = 500)
    private String disputeReason;

    // ── urgency + escalation (V41) ─────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "urgency", nullable = false, length = 20)
    private RequestUrgency urgency = RequestUrgency.ROUTINE;

    /** Auto-expiry deadline; set from the urgency's TTL at creation. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** How many times the alert has been re-broadcast to a wider radius. */
    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel = 0;

    @Column(name = "last_escalated_at")
    private LocalDateTime lastEscalatedAt;

    /** Donor ids already alerted for this request (dedupe across escalations). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "alerted_user_ids", columnDefinition = "jsonb")
    private List<String> alertedUserIds = new ArrayList<>();


    @PrePersist
    protected void onCreate() {
        if (requestTime == null) {
            requestTime = LocalDateTime.now();
        }
        if (status == null) {
            status = RequestStatus.PENDING;
        }
    }
}
