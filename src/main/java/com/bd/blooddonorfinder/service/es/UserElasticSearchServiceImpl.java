package com.bd.blooddonorfinder.service.es;

import com.bd.blooddonorfinder.exception.ElasticSearchOperationException;
import com.bd.blooddonorfinder.model.es.QueryModel.UserQueryModel;
import com.bd.blooddonorfinder.model.es.SearchParam.GeoDonorSearchParams;
import com.bd.blooddonorfinder.model.es.SearchParam.UserSearchParams;
import com.bd.blooddonorfinder.model.es.common.SearchCriteria;
import com.bd.blooddonorfinder.model.es.common.SearchType;
import com.bd.blooddonorfinder.model.es.documents.DonorSearchDocument;
import com.bd.blooddonorfinder.payload.request.NearbyDonorSearchRequest;
import com.bd.blooddonorfinder.payload.response.ListResponse;
import com.bd.blooddonorfinder.payload.response.NearbyDonorSearchResponse;
import com.bd.blooddonorfinder.repository.es.UserElasticSearchCustomRepository;
import com.bd.blooddonorfinder.repository.es.UserElasticSearchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

@Service
@Slf4j
public class UserElasticSearchServiceImpl implements UserElasticSearchService {

    private static final double DEFAULT_INITIAL_RADIUS_KM = 5.0;
    private static final double DEFAULT_RADIUS_STEP_KM = 5.0;
    /**
     * Expansion ladder: +5 km per miss up to {@link #STEP_INCREASE_THRESHOLD_KM},
     * then +10 km per miss, hard cap {@link #DEFAULT_MAX_RADIUS_KM}.
     * Default walk: 5 → 10 → 15 → 20 → 25 → 35 → 45 → 55 → 65 → 75 → 80 km.
     */
    private static final double DEFAULT_MAX_RADIUS_KM = 80.0;
    private static final double DEFAULT_BIG_RADIUS_STEP_KM = 10.0;
    private static final double STEP_INCREASE_THRESHOLD_KM = 25.0;

    private final UserElasticSearchRepository userElasticSearchRepository;
    private final UserElasticSearchCustomRepository userElasticSearchCustomRepository;

    public UserElasticSearchServiceImpl(UserElasticSearchRepository userElasticSearchRepository,
                                        UserElasticSearchCustomRepository userElasticSearchCustomRepository) {
        this.userElasticSearchRepository = userElasticSearchRepository;
        this.userElasticSearchCustomRepository = userElasticSearchCustomRepository;
    }

    @Override
    public ListResponse<DonorSearchDocument> saveAllUsers(List<DonorSearchDocument> users) {
            ListResponse<DonorSearchDocument> response = new ListResponse<>();
            if (users == null || users.isEmpty()) {
                response.setData(Collections.emptyList());
                response.setCount(0);
                return response;
            }
            try {
                StopWatch stopWatch = new StopWatch("saving all users");
                stopWatch.start();
                List<DonorSearchDocument> savedUsers = StreamSupport
                        .stream(userElasticSearchRepository.saveAll(users).spliterator(), false)
                        .toList();
                stopWatch.stop();
                response.setData(savedUsers);
                response.setCount(savedUsers.size());
                response.setTime(stopWatch.getTotalTimeMillis());
                log.info("Saved {} users to Elasticsearch in {} ms", savedUsers.size(), stopWatch.getTotalTimeMillis());
            } catch (Exception e) {
                log.error("Error saving users to Elasticsearch", e);
                throw new ElasticSearchOperationException("Failed to save all users to Elasticsearch", e);
            }

            return response;

    }

    @Override
    public ListResponse<DonorSearchDocument> findByBloodGroup(String bloodGroup) {
        ListResponse<DonorSearchDocument> response = new ListResponse<>();
        if (bloodGroup == null || bloodGroup.isBlank()) {
            response.setData(Collections.emptyList());
            response.setCount(0);
            return response;
        }
        try {
            log.debug("Searching users in Elasticsearch, blood group: {}", bloodGroup);
            StopWatch stopWatch = new StopWatch("searching user by bloodGroup");
            stopWatch.start();
            List<DonorSearchDocument> users = userElasticSearchRepository.findAllByBloodGroup(bloodGroup);
            stopWatch.stop();
            response.setTime(stopWatch.getTotalTimeMillis());

            if (users.isEmpty()) {
                log.info("No users found for blood group {} ({} ms)", bloodGroup, stopWatch.getTotalTimeMillis());
            } else {
                response.setData(users);
                response.setCount(users.size());
                log.info("Found {} users for blood group {} in {} ms", users.size(), bloodGroup, stopWatch.getTotalTimeMillis());
            }
        } catch (Exception e) {
            log.error("Error searching users in Elasticsearch by blood group {}", bloodGroup, e);
            throw new ElasticSearchOperationException("Failed to search users by blood group: " + bloodGroup, e);
        }
        return response;
    }

    @Override
    public ListResponse<DonorSearchDocument> queryForPage(UserSearchParams searchParams) {
        ListResponse<DonorSearchDocument> response = searchParams != null
                ? userElasticSearchCustomRepository.queryForPage(searchParams)
                : new ListResponse<>();
        DonorDocumentSanitizer.sanitizeAll(response.getData());
        return response;
    }

    /** Freshness window for nearby searches — donors with older coordinates are excluded. */
    @Value("${app.search.max-location-age-days:90}")
    private long defaultMaxLocationAgeDays;

    @Override
    public NearbyDonorSearchResponse searchNearbyDonors(NearbyDonorSearchRequest request) {
        if (request == null || request.getLatitude() == null || request.getLongitude() == null) {
            throw new IllegalArgumentException("latitude and longitude are required for nearby donor search");
        }

        double latitude = request.getLatitude();
        double longitude = request.getLongitude();
        double initialRadius = request.getInitialRadiusKm() != null && request.getInitialRadiusKm() > 0
                ? request.getInitialRadiusKm() : DEFAULT_INITIAL_RADIUS_KM;
        double step = request.getRadiusStepKm() != null && request.getRadiusStepKm() > 0
                ? request.getRadiusStepKm() : DEFAULT_RADIUS_STEP_KM;
        double maxRadius = request.getMaxRadiusKm() != null
                ? Math.min(request.getMaxRadiusKm(), DEFAULT_MAX_RADIUS_KM) : DEFAULT_MAX_RADIUS_KM;
        // null maxExpansions = governed only by the radius cap; caller-set values act
        // as an additional bound (0 = search the initial radius only)
        Integer maxExpansions = request.getMaxExpansions();

        UserSearchParams searchParams = new UserSearchParams();
        searchParams.setQueryModel(buildQueryModel(request));
        searchParams.setLength(request.getLength());
        searchParams.setSearchAfter(request.getSearchAfter());

        Long maxLocationAgeDays = request.getMaxLocationAgeDays() != null
                ? request.getMaxLocationAgeDays().longValue() : defaultMaxLocationAgeDays;

        double radius = Math.min(initialRadius, maxRadius);
        int expansions = 0;
        ListResponse<DonorSearchDocument> result = null;

        while (true) {
            GeoDonorSearchParams geoParams = GeoDonorSearchParams.of(searchParams, latitude, longitude, radius, maxLocationAgeDays);
            result = userElasticSearchCustomRepository.queryForPage(geoParams);
            if (result.getCount() > 0) {
                break;
            }
            // next attempt: +step below the threshold, +big step above it; the final
            // attempt lands exactly on the cap so the widest circle is always searched
            double stepForNext = radius >= STEP_INCREASE_THRESHOLD_KM ? DEFAULT_BIG_RADIUS_STEP_KM : step;
            double next = Math.min(radius + stepForNext, maxRadius);
            boolean expansionBoundReached = maxExpansions != null && expansions >= maxExpansions;
            if (radius >= maxRadius || expansionBoundReached) {
                break;
            }
            double searchedKm = radius;
            radius = next;
            expansions++;
            log.info("No donors within {} km, expanding search radius to {} km", searchedKm, radius);
        }

        NearbyDonorSearchResponse response = NearbyDonorSearchResponse.builder()
                .donors(result.getData() != null ? result.getData() : Collections.emptyList())
                .totalHits(result.getCount())
                .timeMs(result.getTime())
                .latitude(latitude)
                .longitude(longitude)
                .searchRadiusKm(radius)
                .expansions(expansions)
                .searchAfter(result.getSearchAfter())
                .build();
        DonorDocumentSanitizer.sanitizeAll(response.getDonors());

        log.info("Nearby donor search at ({},{}): {} donors within {} km after {} expansion(s)",
                latitude, longitude, response.getTotalHits(), radius, expansions);
        return response;
    }

    private UserQueryModel buildQueryModel(NearbyDonorSearchRequest request) {
        UserQueryModel queryModel = new UserQueryModel();
        if (request.getBloodGroup() != null && !request.getBloodGroup().isBlank()) {
            queryModel.setBloodGroup(new SearchCriteria(request.getBloodGroup(), SearchType.exact));
        }
        if (request.getIsVerified() != null) {
            queryModel.setIsVerified(new SearchCriteria(request.getIsVerified().toString(), SearchType.exact));
        }
        if (request.getIsAvailable() != null) {
            queryModel.setIsAvailable(new SearchCriteria(request.getIsAvailable().toString(), SearchType.exact));
        }
        return queryModel;
    }
}
