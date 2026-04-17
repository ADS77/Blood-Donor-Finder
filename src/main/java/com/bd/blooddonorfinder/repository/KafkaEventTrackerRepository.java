package com.bd.blooddonorfinder.repository;

import com.bd.blooddonorfinder.kafka.model.EventTracker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KafkaEventTrackerRepository extends JpaRepository<EventTracker, Long> {

    boolean existsByEventId(String eventId);
}
