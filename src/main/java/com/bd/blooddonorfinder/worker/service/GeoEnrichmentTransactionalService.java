package com.bd.blooddonorfinder.worker.service;

import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import com.bd.blooddonorfinder.kafka.model.events.UserGeoEnrichedEvent;
import com.bd.blooddonorfinder.kafka.producer.GenericKafkaEventProducer;
import com.bd.blooddonorfinder.model.common.GeoLocation;
import com.bd.blooddonorfinder.payload.response.GeoResponse;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.GeoStatus;
import com.bd.blooddonorfinder.repository.UserRepository;
import com.bd.blooddonorfinder.service.GeoLocationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class GeoEnrichmentTransactionalService {
    private final UserRepository userRepository;
    private final GeoLocationService geoLocationService;
    private final GenericKafkaEventProducer eventProducer;

    private static final int MAX_RETRY_ATTEMPTS = 5;

    public GeoEnrichmentTransactionalService(UserRepository userRepository,
                                             GeoLocationService geoLocationService,
                                             GenericKafkaEventProducer eventProducer) {
        this.userRepository = userRepository;
        this.geoLocationService = geoLocationService;
        this.eventProducer = eventProducer;
    }

    @Transactional
    public void enrichSingleUser(UUID userId) {
        int claim = userRepository.claimForGeoEnrichment(userId);
        if(claim == 0){
            log.info("Already claimed by another instance for geo enrichment, userId = {}", userId);
            return;
        }

        Optional<User> user = userRepository.findById(userId);
        if(user.isPresent()){
            GeoLocation geoLocation = user.get().getGeoLocation();
            try {
                GeoResponse geoResponse = geoLocationService.getLatLong(geoLocation.getCity());
                if(geoResponse != null && geoResponse.isSuccess()){
                    UserGeoEnrichedEvent  geoEnrichedEvent = UserGeoEnrichedEvent.from(user.get(), geoResponse);
                    publishEventAfterCommit(geoEnrichedEvent);
                }else {
                    handleFailure(geoLocation, userId, "Geocoding service returned no result");

                }
            }catch (Exception e){
                handleFailure(geoLocation, userId,e.getMessage());
            }
        }
    }

    private void publishEventAfterCommit(BaseEvent geoEnrichedEvent) {
        if(TransactionSynchronizationManager.isSynchronizationActive()){
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            eventProducer.publishEvent(geoEnrichedEvent);
                        }
                    }
            );
        }else {
            eventProducer.publishEvent(geoEnrichedEvent);
        }
    }

    private void handleFailure(GeoLocation geo, UUID userId, String reason) {
        int attempts = geo.getGeoRetryCount() + 1;
        GeoStatus nextGeoStatus = attempts >= MAX_RETRY_ATTEMPTS ? GeoStatus.FAILED : GeoStatus.PENDING;
        userRepository.updateGeoEnrichmentFailureStatus(userId, nextGeoStatus, attempts, truncate(reason));
        log.warn("Geo enrichment attempt {}/{} failed for userId={}: {} -> next status {}",
                attempts, MAX_RETRY_ATTEMPTS, userId, reason, nextGeoStatus);
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}
