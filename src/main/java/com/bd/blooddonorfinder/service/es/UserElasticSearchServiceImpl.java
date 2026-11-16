package com.bd.blooddonorfinder.service.es;

import com.bd.blooddonorfinder.exception.ElasticSearchOperationException;
import com.bd.blooddonorfinder.model.es.Location;
import com.bd.blooddonorfinder.model.es.QueryModel.UserQueryModel;
import com.bd.blooddonorfinder.model.es.SearchParam.GeoDonorSearchParams;
import com.bd.blooddonorfinder.model.es.SearchParam.UserSearchParams;
import com.bd.blooddonorfinder.model.es.common.SearchCriteria;
import com.bd.blooddonorfinder.model.es.common.SearchType;
import com.bd.blooddonorfinder.model.es.documents.DonorSearchDocument;
import com.bd.blooddonorfinder.model.es.documents.RegisteredDonorDocument;
import com.bd.blooddonorfinder.payload.request.NearbyDonorSearchRequest;
import com.bd.blooddonorfinder.payload.response.ListResponse;
import com.bd.blooddonorfinder.payload.response.NearbyDonorSearchResponse;
import com.bd.blooddonorfinder.repository.es.RegisteredDonorDocumentRepository;
import com.bd.blooddonorfinder.repository.es.UserElasticSearchCustomRepository;
import com.bd.blooddonorfinder.repository.es.UserElasticSearchRepository;
import com.bd.blooddonorfinder.service.geo.DonorGeoRedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final DonorGeoRedisService donorGeoRedisService;
    private final RegisteredDonorDocumentRepository registeredDonorDocumentRepository;

    public UserElasticSearchServiceImpl(UserElasticSearchRepository userElasticSearchRepository,
                                        UserElasticSearchCustomRepository userElasticSearchCustomRepository,
                                        DonorGeoRedisService donorGeoRedisService,
                                        RegisteredDonorDocumentRepository registeredDonorDocumentRepository) {
        this.userElasticSearchRepository = userElasticSearchRepository;
        this.userElasticSearchCustomRepository = userElasticSearchCustomRepository;
        this.donorGeoRedisService = donorGeoRedisService;
        this.registeredDonorDocumentRepository = registeredDonorDocumentRepository;
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

        // Redis GEO fast path: one GEOSEARCH round trip per radius instead of an
        // ES query. Serves first-page, cursor-free searches; availability
        // filtering and search_after pagination fall through to Elasticsearch.
        NearbyDonorSearchResponse fastPath = searchViaRedisFastPath(
                request, latitude, longitude, initialRadius, step, maxRadius, maxExpansions,
                maxLocationAgeDays);
        if (fastPath != null) {
            return fastPath;
        }

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

    /**
     * Redis GEO fast path. Walks the same expansion ladder as the ES loop, but
     * each probe is a single Redis GEOSEARCH; the first radius with members
     * wins, their ES documents are fetched by id and filtered in memory.
     *
     * <p>Returns null — meaning "use Elasticsearch" — when the fast path is
     * disabled, the request needs features Redis can't serve (cursor
     * pagination, availability filtering), the Redis set is cold/missing, or
     * nobody is found within the capped radius.</p>
     */
    private NearbyDonorSearchResponse searchViaRedisFastPath(NearbyDonorSearchRequest request,
                                                             double latitude, double longitude,
                                                             double initialRadius, double step,
                                                             double maxRadius, Integer maxExpansions,
                                                             Long maxLocationAgeDays) {
        if (!donorGeoRedisService.isEnabled()) {
            return null;
        }
        // Features the fast path cannot serve exactly — let ES handle them
        if (request.getSearchAfter() != null && request.getSearchAfter().length > 0) {
            return null;
        }
        if (request.getIsAvailable() != null) {
            return null;
        }

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        double radius = Math.min(initialRadius, maxRadius);
        int expansions = 0;
        while (true) {
            List<String> ids = donorGeoRedisService.searchNearest(latitude, longitude, radius);
            if (ids != null && !ids.isEmpty()) {
                List<DonorSearchDocument> donors = hydrateAndFilter(
                        ids, request.getBloodGroup(), request.getIsVerified(), maxLocationAgeDays);
                if (!donors.isEmpty()) {
                    int pageSize = request.getLength() != null && request.getLength() > 0
                            ? Math.min(request.getLength(), 100) : 10;
                    if (donors.size() > pageSize) {
                        donors = donors.subList(0, pageSize);
                    }
                    return buildFastPathResponse(donors, latitude, longitude, radius, expansions,
                            stopWatch.getTotalTimeMillis());
                }
                // members in range but nobody matched the filters — keep expanding
            }
            if (ids == null) {
                // Redis has no usable data (cold set, flushed, or error): ES fallback
                log.info("Redis GEO fast path unavailable at radius {} km — falling back to Elasticsearch", radius);
                return null;
            }
            double stepForNext = radius >= STEP_INCREASE_THRESHOLD_KM ? DEFAULT_BIG_RADIUS_STEP_KM : step;
            double next = Math.min(radius + stepForNext, maxRadius);
            boolean expansionBoundReached = maxExpansions != null && expansions >= maxExpansions;
            if (radius >= maxRadius || expansionBoundReached) {
                log.info("Redis GEO fast path found no donors within {} km — falling back to Elasticsearch", radius);
                return null;
            }
            radius = next;
            expansions++;
        }
    }

    /**
     * Fetches the ES documents for the Redis hits (nearest-first order is
     * preserved) and applies the same filters the ES geo query would:
     * blood group, verification and the coordinate freshness window.
     * Ids whose document no longer exists (deleted donors, stale members) are
     * skipped — Redis has no user.deleted wiring yet.
     */
    private List<DonorSearchDocument> hydrateAndFilter(List<String> ids,
                                                       String bloodGroup, Boolean isVerified,
                                                       Long maxLocationAgeDays) {
        List<RegisteredDonorDocument> docs = new ArrayList<>();
        registeredDonorDocumentRepository.findAllById(ids).forEach(docs::add);
        // preserve nearest-first ordering from the GEOSEARCH
        Map<String, RegisteredDonorDocument> byId = new LinkedHashMap<>();
        docs.forEach(doc -> byId.put(doc.getId(), doc));

        long freshnessCutoff = maxLocationAgeDays != null && maxLocationAgeDays > 0
                ? Instant.now().minus(Duration.ofDays(maxLocationAgeDays)).toEpochMilli()
                : 0L;

        List<DonorSearchDocument> donors = new ArrayList<>();
        for (String id : ids) {
            RegisteredDonorDocument doc = byId.get(id);
            if (doc == null || doc.getLocation() == null) {
                continue; // stale member — ES document already gone
            }
            if (bloodGroup != null && !bloodGroup.isBlank()
                    && !bloodGroup.equalsIgnoreCase(doc.getBloodGroup())) {
                continue;
            }
            if (isVerified != null && !isVerified.equals(doc.getIsVerified())) {
                continue;
            }
            // Missing geoUpdatedAt = pre-pipeline doc: searchable, as in ES
            if (freshnessCutoff > 0 && doc.getGeoUpdatedAt() != null
                    && doc.getGeoUpdatedAt() < freshnessCutoff) {
                continue;
            }
            donors.add(toSearchDocument(doc));
        }
        return donors;
    }

    /** Maps the indexed shape to the search read model, matching ES backfill semantics. */
    private DonorSearchDocument toSearchDocument(RegisteredDonorDocument doc) {
        DonorSearchDocument out = new DonorSearchDocument();
        out.setId(doc.getId());
        out.setName(doc.getUsername());
        out.setBloodGroup(doc.getBloodGroup());
        out.setIsVerified(doc.getIsVerified());
        out.setRoles(doc.getRoles());
        out.setGeoUpdatedAt(doc.getGeoUpdatedAt());
        if (doc.getLastDonationDate() != null) {
            out.setLastDonationDate(doc.getLastDonationDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (doc.getCreatedAt() != null) {
            out.setCreatedAt(doc.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (doc.getUpdatedAt() != null) {
            out.setUpdatedAt(doc.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        Location location = new Location();
        location.setLatitude(doc.getLocation().getLat());
        location.setLongitude(doc.getLocation().getLon());
        location.setCity(doc.getCity());
        location.setDistrict(doc.getDistrict());
        location.setZipcode(doc.getZipcode());
        out.setLocation(location);
        return out;
    }

    private NearbyDonorSearchResponse buildFastPathResponse(List<DonorSearchDocument> donors,
                                                            double latitude, double longitude,
                                                            double radius, int expansions, long timeMs) {
        // page size semantics identical to the ES path
        NearbyDonorSearchResponse response = NearbyDonorSearchResponse.builder()
                .donors(donors)
                .totalHits((long) donors.size())
                .timeMs(timeMs)
                .latitude(latitude)
                .longitude(longitude)
                .searchRadiusKm(radius)
                .expansions(expansions)
                .searchAfter(null)
                .build();
        DonorDocumentSanitizer.sanitizeAll(response.getDonors());
        log.info("Redis fast path: {} donors within {} km after {} expansion(s) ({} ms)",
                donors.size(), radius, expansions, timeMs);
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
