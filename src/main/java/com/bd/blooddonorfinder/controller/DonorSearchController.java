package com.bd.blooddonorfinder.controller;

import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.payload.request.DonorSearchRequest;
import com.bd.blooddonorfinder.payload.response.ListResponse;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.service.DonorSearchService;
import com.bd.blooddonorfinder.service.NotificationManager;
import com.bd.blooddonorfinder.utils.DonorUtils;
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

    private final DonorSearchService donorSearchService;
    private final NotificationManager notificationManager;
    private final Double defaultRadius;

    public DonorSearchController(DonorSearchService donorSearchService,
                                 NotificationManager notificationManager,
                                 @Value("${default.geo.search.radius:30}") Double defaultRadius) {
        this.donorSearchService = donorSearchService;
        this.notificationManager = notificationManager;
        this.defaultRadius = defaultRadius;
    }

    @PostMapping("/eligible-donors")
    public ResponseEntity<RestApiResponse<ListResponse<User>>> getEligibleDonors(
            @Valid @RequestBody DonorSearchRequest donorSearchRequest) {
        if (donorSearchRequest.getRadius() == null || donorSearchRequest.getRadius() <= 0) {
            donorSearchRequest.setRadius(defaultRadius);
        }
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        List<User> eligibleDonors = donorSearchService.findNearByEligibleDonors(donorSearchRequest);
        stopWatch.stop();
        log.info("Found {} eligible donors", eligibleDonors.size());
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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN')")
    @PostMapping("/notify-near-by-donors")
    public ResponseEntity<RestApiResponse<List<User>>> notifyNearByDonorsByEmail(
            @Valid @RequestBody DonorSearchRequest donorSearchRequest) {
        if (donorSearchRequest.getRadius() == null || donorSearchRequest.getRadius() <= 0) {
            donorSearchRequest.setRadius(defaultRadius);
        }
        List<User> nearByUserList = donorSearchService.findNearByDonors(donorSearchRequest);
        List<User> eligibleDonors = DonorUtils.filterEligibleDonors(nearByUserList);
        notificationManager.notifyByMail(eligibleDonors, donorSearchRequest);
        log.debug("Notifying {} eligible donors", eligibleDonors.size());
        return ResponseEntity.ok()
                .body(RestApiResponse.success(eligibleDonors.size(),
                        "Notified " + eligibleDonors.size() + " nearby donors",
                        HttpStatus.OK));
    }
}
