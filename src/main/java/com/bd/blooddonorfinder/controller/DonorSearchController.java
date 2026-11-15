package com.bd.blooddonorfinder.controller;

import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.es.SearchParam.UserSearchParams;
import com.bd.blooddonorfinder.model.es.documents.DonorSearchDocument;
import com.bd.blooddonorfinder.payload.request.DonorSearchRequest;
import com.bd.blooddonorfinder.payload.request.NearbyDonorSearchRequest;
import com.bd.blooddonorfinder.payload.response.ListResponse;
import com.bd.blooddonorfinder.payload.response.NearbyDonorSearchResponse;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.service.DonorSearchService;
import com.bd.blooddonorfinder.service.NotificationManager;
import com.bd.blooddonorfinder.service.es.UserElasticSearchService;
import com.bd.blooddonorfinder.utils.DonorUtils;
import com.bd.blooddonorfinder.utils.Utils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@Slf4j
public class DonorSearchController {

    private static final double MIN_RADIUS_KM = 1.0;
    private static final double MAX_RADIUS_KM = 100.0;

    private final DonorSearchService donorSearchService;
    private final NotificationManager notificationManager;
    private final UserElasticSearchService userElasticSearchService;
    private final Double defaultRadius;

    public DonorSearchController(DonorSearchService donorSearchService,
                                 NotificationManager notificationManager,
                                 UserElasticSearchService userElasticSearchService,
                                 @Value("${default.geo.search.radius:30}") Double defaultRadius) {
        this.donorSearchService = donorSearchService;
        this.notificationManager = notificationManager;
        this.userElasticSearchService = userElasticSearchService;
        this.defaultRadius = defaultRadius;
    }

    @PostMapping("/donors")
    public ResponseEntity<RestApiResponse<ListResponse<DonorSearchDocument>>> searchDonorsFromElastic(
            @RequestBody(required = false) UserSearchParams searchParams) {
        log.debug("Searching donors from Elasticsearch with params: {}", searchParams);
        try {
            ListResponse<DonorSearchDocument> result = userElasticSearchService.queryForPage(
                    searchParams != null ? searchParams : new UserSearchParams());
            RestApiResponse<ListResponse<DonorSearchDocument>> response =
                    Utils.buildSuccessRestResponse(HttpStatus.OK, result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error while searching donors from Elasticsearch", e);
            RestApiResponse<ListResponse<DonorSearchDocument>> response =
                    Utils.buildErrorRestResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                            "ElasticDonorSearch", "Error while searching donors from Elasticsearch");
            return ResponseEntity.status(response.getStatus()).body(response);
        }
    }

    /**
     * Nearby donor search with expanding radius:
     * starts at 5 km, +5 km per miss up to 25 km, then +10 km per miss,
     * expanding up to 80 km until donors are found.
     */
    @PostMapping("/donors/nearby")
    public ResponseEntity<RestApiResponse<NearbyDonorSearchResponse>> searchNearbyDonors(
            @Valid @RequestBody NearbyDonorSearchRequest request) {
        log.debug("Nearby donor search request: {}", request);
        try {
            NearbyDonorSearchResponse result = userElasticSearchService.searchNearbyDonors(request);
            RestApiResponse<NearbyDonorSearchResponse> response =
                    Utils.buildSuccessRestResponse(HttpStatus.OK, result);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid nearby donor search request: {}", e.getMessage());
            RestApiResponse<NearbyDonorSearchResponse> response =
                    Utils.buildErrorRestResponse(HttpStatus.BAD_REQUEST,
                            "NearbyDonorSearch", e.getMessage());
            return ResponseEntity.status(response.getStatus()).body(response);
        } catch (Exception e) {
            log.error("Error while searching nearby donors from Elasticsearch", e);
            RestApiResponse<NearbyDonorSearchResponse> response =
                    Utils.buildErrorRestResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                            "NearbyDonorSearch", "Error while searching nearby donors from Elasticsearch");
            return ResponseEntity.status(response.getStatus()).body(response);
        }
    }

    @PostMapping("/eligible-donors")
    public ResponseEntity<RestApiResponse<ListResponse<User>>> getEligibleDonors(
            @Valid @RequestBody DonorSearchRequest donorSearchRequest) {
        // Clamp caller-supplied radius instead of only bumping small values up.
        donorSearchRequest.setRadius(clampRadius(donorSearchRequest.getRadius()));
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        List<User> eligibleDonors = donorSearchService.findNearByEligibleDonors(donorSearchRequest);
        stopWatch.stop();
        // SECURITY: log the count only — donor entities carry PII.
        log.info("Found {} eligible donors (radius={} km)",
                eligibleDonors.size(), donorSearchRequest.getRadius());
        ListResponse<User> eligibleDonorList = new ListResponse<>();
        eligibleDonorList.setData(eligibleDonors);
        eligibleDonorList.setTime(stopWatch.getTotalTimeMillis());
        eligibleDonorList.setCount(eligibleDonors.size());
        return ResponseEntity.ok()
                .body(RestApiResponse.success(eligibleDonorList, "Eligible donors fetched"));
    }

    /**
     * Bulk-notifies donors. This is an administrative operation because it can
     * send mail to many users and is not something a regular donor may trigger.
     */
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN')")
    @PostMapping("/notify-near-by-donors")
    public ResponseEntity<RestApiResponse<List<User>>> notifyNearByDonorsByEmail(
            @Valid @RequestBody DonorSearchRequest donorSearchRequest) {
        donorSearchRequest.setRadius(clampRadius(donorSearchRequest.getRadius()));
        List<User> nearByUserList = donorSearchService.findNearByDonors(donorSearchRequest);
        List<User> eligibleDonors = DonorUtils.filterEligibleDonors(nearByUserList);
        notificationManager.notifyByMail(eligibleDonors, donorSearchRequest);
        log.debug("Notifying {} eligible donors", eligibleDonors.size());
        return ResponseEntity.ok()
                .body(RestApiResponse.success(eligibleDonors.size(),
                        "Notified " + eligibleDonors.size() + " nearby donors",
                        HttpStatus.OK));
    }

    private double clampRadius(Double radius) {
        double value = radius == null || radius <= 0 ? defaultRadius : radius;
        return Math.min(Math.max(value, MIN_RADIUS_KM), MAX_RADIUS_KM);
    }
}
