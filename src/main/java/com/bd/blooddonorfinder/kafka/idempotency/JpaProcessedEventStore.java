package com.bd.blooddonorfinder.kafka.idempotency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
@Service
@Slf4j
public class JpaProcessedEventStore implements ProcessedEventStore{
    private final ProcessedEventRepository repository;

    public JpaProcessedEventStore(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markProcessedIfAbsent(String eventId, String topicName, String aggregateId) {
        if(repository.existsById(eventId)){
            return false;
        }
        try {
            repository.saveAndFlush(ProcessedEvent.builder()
            .eventId(eventId)
            .topicName(topicName)
            .aggregateId(aggregateId)
            .processedAt(Instant.now())
            .build());
            return true;
        }catch (DataIntegrityViolationException e){
            log.info("Duplicate eventId={} detected on insert", eventId);
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isProcessed(String eventId) {
        return repository.existsById(eventId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensate(String eventId) {
        repository.deleteById(eventId);
        log.info("Compensated, released idempotency claim for eventId = {}", eventId);

    }
}
