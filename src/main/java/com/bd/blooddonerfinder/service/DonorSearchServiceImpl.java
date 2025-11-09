package com.bd.blooddonerfinder.service;

import com.bd.blooddonerfinder.model.GeoLocation;
import com.bd.blooddonerfinder.model.GeoResponse;
import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.model.enums.Role;
import com.bd.blooddonerfinder.payload.request.DonorSearchRequest;
import com.bd.blooddonerfinder.repository.UserRepository;
import com.bd.blooddonerfinder.util.DonorUtils;
import com.bd.blooddonerfinder.util.GeoUtils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

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
        List<User> allDonors = userRepository.findNearByRoleAndBloodGroup(Role.DONOR, searchRequest.getBloodGroup());
        if(searchRequest.getGeoLocation().getLatitude() == null || searchRequest.getGeoLocation().getLongitude() == null){
            GeoResponse requesterGeo = geoLocationService.getLatLong(searchRequest.getGeoLocation().getCity());
            searchRequest.getGeoLocation().setLatitude(Double.parseDouble(requesterGeo.getLatitude()));
            searchRequest.getGeoLocation().setLongitude(Double.parseDouble(requesterGeo.getLongitude()));
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
                        GeoResponse geo = geoLocationService.getLatLong(
                                donor.getGeoLocation().getCity()
                        );
                        donor.getGeoLocation().setLatitude(Double.parseDouble(geo.getLatitude()));
                        donor.getGeoLocation().setLongitude(Double.parseDouble(geo.getLongitude()));
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
        return donors.stream()
                .filter(donor -> {
                    GeoLocation geoLocation = donor.getGeoLocation();
                    if (geoLocation == null || geoLocation.getLatitude() == null
                            || geoLocation.getLongitude() == null) {
                        return false;
                    }
                    double distance = GeoUtils.haversine(
                            searchRequest.getGeoLocation().getLatitude(),
                            geoLocation.getLatitude(),
                            searchRequest.getGeoLocation().getLongitude(),
                            geoLocation.getLongitude()
                    );
                    return distance <= searchRequest.getRadius();
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<User> findNearByEligibleDonors(DonorSearchRequest searchRequest) {
        List<User> nearByDonors = findNearByDonors(searchRequest);
        List<User> eligibleDonors = DonorUtils.filterEligibleDonors(nearByDonors);
        return eligibleDonors;
    }


}
