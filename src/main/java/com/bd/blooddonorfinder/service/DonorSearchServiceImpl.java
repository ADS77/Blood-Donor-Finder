package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.model.common.GeoLocation;
import com.bd.blooddonorfinder.payload.response.GeoResponse;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.payload.request.DonorSearchRequest;
import com.bd.blooddonorfinder.repository.UserRepository;
import com.bd.blooddonorfinder.utils.DonorUtils;
import com.bd.blooddonorfinder.utils.GeoUtils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DonorSearchServiceImpl implements DonorSearchService{
    private final UserRepository userRepository;
    private final GeoLocationService geoLocationService;
    private final Executor geoFetchExecutor;

    /** Freshness window — donors with older coordinates are excluded from nearby/notify flows. */
    @Value("${app.search.max-location-age-days:90}")
    private long maxLocationAgeDays;

    public DonorSearchServiceImpl(UserRepository userRepository,
                                  GeoLocationService geoLocationService,
                                  @Qualifier("geoFetchExecutor") Executor geoFetchExecutor) {
        this.userRepository = userRepository;

        this.geoLocationService = geoLocationService;
        this.geoFetchExecutor = geoFetchExecutor;
    }

    @Override
    public List<User> findNearByDonors(@Valid DonorSearchRequest searchRequest) {
        log.info("Searching donors nearby :{}", searchRequest.getGeoLocation().getCity());
        List<User> allDonors = userRepository.findNearByAndBloodGroupAndGeoLocationCity(
                searchRequest.getBloodGroup(),
                searchRequest.getGeoLocation().getCity());
        log.info("Found {} nearby donors in {}", allDonors.size(), searchRequest.getGeoLocation().getCity());

        if(searchRequest.getGeoLocation().getLatitude() == null || searchRequest.getGeoLocation().getLongitude() == null){
            GeoResponse requesterGeo = geoLocationService.getLatLong(searchRequest.getGeoLocation().getCity());
            if(requesterGeo != null){
                searchRequest.getGeoLocation().setLatitude((requesterGeo.getLatitude()));
                searchRequest.getGeoLocation().setLongitude(requesterGeo.getLongitude());
            }
        }
        Map<Boolean, List<User>> partitioned = allDonors.stream()
                .collect(Collectors.partitioningBy(
                        donor -> donor.getGeoLocation() != null
                                && donor.getGeoLocation().getLatitude() != null
                ));

        List<User> withCoords = partitioned.get(true);
        List<User> withoutCoords = partitioned.get(false);
        List<CompletableFuture<User>> futures = withoutCoords.stream()
                .limit(10)
                .map(donor -> CompletableFuture.supplyAsync(() -> {
                    try {
                        GeoResponse geo = geoLocationService.getLatLong(donor.getGeoLocation().getCity());
                        if(geo != null){
                            donor.getGeoLocation().setLatitude(geo.getLatitude());
                            donor.getGeoLocation().setLongitude(geo.getLongitude());
                        }
                        userRepository.save(donor);
                        return donor;
                    } catch (Exception e) {
                        return null;
                    }
                }, geoFetchExecutor))
                .collect(Collectors.toList());

        List<User> enriched = futures.stream()
                .map(f -> f.orTimeout(5, TimeUnit.SECONDS))
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<User> allDonorsWithCoords = new ArrayList<>();
        allDonorsWithCoords.addAll(withCoords);
        allDonorsWithCoords.addAll(enriched);

        return filterByDistance(allDonorsWithCoords, searchRequest);
    }

    private List<User> filterByDistance(List<User> donors, DonorSearchRequest searchRequest) {
        // Location-freshness pipeline: a donor whose coordinates have not been
        // confirmed within the freshness window may live somewhere else now —
        // notifying them would page donors far away from the requester.
        Instant freshnessCutoff = Instant.now().minus(Duration.ofDays(maxLocationAgeDays));
        List<User> result = new ArrayList<>(donors.size());
        int staleCount = 0;
        for (User donor : donors) {
            GeoLocation geoLocation = donor.getGeoLocation();
            if (geoLocation == null || geoLocation.getLatitude() == null
                    || geoLocation.getLongitude() == null) {
                continue;
            }
            // Legacy rows are backfilled by V33; null is treated defensively as fresh.
            if (geoLocation.getGeoUpdatedAt() != null
                    && geoLocation.getGeoUpdatedAt().isBefore(freshnessCutoff)) {
                staleCount++;
                continue;
            }
            double distance = GeoUtils.haversine(
                    GeoUtils.sanitizeGeoUnit(searchRequest.getGeoLocation().getLatitude()),
                    GeoUtils.sanitizeGeoUnit(geoLocation.getLatitude()),
                    GeoUtils.sanitizeGeoUnit(searchRequest.getGeoLocation().getLongitude()),
                    GeoUtils.sanitizeGeoUnit(geoLocation.getLongitude())
            );
            if (distance <= searchRequest.getRadius()) {
                result.add(donor);
            }
        }
        if (staleCount > 0) {
            log.info("Excluded {} donor(s) with unconfirmed location older than {} days",
                    staleCount, maxLocationAgeDays);
        }
        return result;
    }

    @Override
    public List<User> findNearByEligibleDonors(DonorSearchRequest searchRequest) {
        List<User> nearByDonors = findNearByDonors(searchRequest);
        List<User> eligibleDonors = DonorUtils.filterEligibleDonors(nearByDonors);
        return eligibleDonors;
    }


}
