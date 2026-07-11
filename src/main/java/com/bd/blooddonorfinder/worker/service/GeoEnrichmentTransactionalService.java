package com.bd.blooddonorfinder.worker.service;

import com.bd.blooddonorfinder.model.GeoLocation;
import com.bd.blooddonorfinder.model.GeoResponse;
import com.bd.blooddonorfinder.model.User;
import com.bd.blooddonorfinder.model.enums.GeoStatus;
import com.bd.blooddonorfinder.repository.UserRepository;
import com.bd.blooddonorfinder.service.GeoLocationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class GeoEnrichmentTransactionalService {
    private final UserRepository userRepository;
    private final GeoLocationService geoLocationService;

    private static final int MAX_RETRY_ATTEMPTS = 5;

    public GeoEnrichmentTransactionalService(UserRepository userRepository, GeoLocationService geoLocationService) {
        this.userRepository = userRepository;
        this.geoLocationService = geoLocationService;
    }

    @Transactional
    public void enrichSingleUser(Long userId) {
        User locked = userRepository.findByIdForUpdate(userId).orElse(null);
        if (locked == null || locked.getGeoLocation().getGeoStatus() != GeoStatus.PENDING) {
            return;
        }

        GeoLocation geo = locked.getGeoLocation();

        try {
            GeoResponse response = geoLocationService.getLatLong(geo.getCity());

            if (response != null && response.isSuccess()) {
                geo.setLatitude(response.getLatitude());
                geo.setLongitude(response.getLongitude());
                geo.setGeoStatus(GeoStatus.COMPLETED);
                geo.setGeoLastError(null);
                log.info("Geo enrichment succeeded for userId={}", userId);
            } else {
                handleFailure(geo, userId, "Geocoding service returned no result");
            }
        } catch (Exception e) {
            handleFailure(geo, userId, e.getMessage());
        }

        userRepository.save(locked);
    }

    private void handleFailure(GeoLocation geo, Long userId, String reason) {
        int attempts = geo.getGeoRetryCount() + 1;
        geo.setGeoRetryCount(attempts);
        geo.setGeoLastError(truncate(reason, 500));

        if (attempts >= MAX_RETRY_ATTEMPTS) {
            geo.setGeoStatus(GeoStatus.FAILED);
            log.warn("Geo enrichment permanently failed for userId={} after {} attempts", userId, attempts);
        } else {
            geo.setGeoStatus(GeoStatus.PENDING);
            log.warn("Geo enrichment attempt {}/{} failed for userId={}", attempts, MAX_RETRY_ATTEMPTS, userId);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
