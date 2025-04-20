package com.bd.blooddonerfinder.model;

import com.bd.blooddonerfinder.model.enums.BloodGroup;
import com.bd.blooddonerfinder.model.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class BloodRequest {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private User requester;

    @Enumerated(EnumType.STRING)
    private BloodGroup neededGroup;

    private String message;
    private LocalDateTime requestTime;

    @Embedded
    private Location location;

    private RequestStatus status;

    private int requiredQuantity;

}
