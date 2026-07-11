package com.bd.blooddonorfinder.kafka.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "topic_name", nullable = false)
    private String topicName;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
