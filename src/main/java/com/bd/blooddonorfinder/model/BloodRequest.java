package com.bd.blooddonorfinder.model;

import com.bd.blooddonorfinder.model.enums.BloodGroup;
import com.bd.blooddonorfinder.model.enums.RequestStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "blood_request")
public class BloodRequest implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
