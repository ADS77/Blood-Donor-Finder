package com.bd.blooddonorfinder.kafka.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_tracker", indexes = {
        @Index(name = "idx_event_id", columnList = "event_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventTracker {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, name = "event_id")
    private String eventId;

    @Column(nullable = false,  name = "event_type")
    private String eventType;

    @Column(nullable = false)
    private String status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime processedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}
