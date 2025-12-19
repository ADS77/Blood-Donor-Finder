package com.bd.blooddonerfinder.controller;

import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.model.common.ListResponse;
import com.bd.blooddonerfinder.payload.request.DonorSearchRequest;
import com.bd.blooddonerfinder.payload.response.RestApiResponse;
import com.bd.blooddonerfinder.service.DonorSearchService;
import com.bd.blooddonerfinder.service.NotificationManager;
import com.bd.blooddonerfinder.util.DonorUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@Slf4j
public class DonorSearchController {
    //@Value("${default.geo.search.radius}")
    private String defaultRadius = "30";
    private Double RADIUS = Double.parseDouble(defaultRadius);
    private final DonorSearchService donorSearchService;
    private final NotificationManager notificationManager;

    public DonorSearchController(DonorSearchService donorSearchService, NotificationManager notificationManager) {
        this.donorSearchService = donorSearchService;
        this.notificationManager = notificationManager;
    }

    @PostMapping("/eligible-donors")
    public ResponseEntity<RestApiResponse<ListResponse<User>>> getEligibleDonors(@RequestBody DonorSearchRequest donorSearchRequest){
        if (donorSearchRequest.getRadius() <= 30){
            donorSearchRequest.setRadius(RADIUS);
        }
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        List<User> eligibleDonors = donorSearchService.findNearByEligibleDonors(donorSearchRequest);
        stopWatch.stop();
        log.info("Eligible Donor List : {}", eligibleDonors);
        ListResponse<User> eligibleDonorList = new ListResponse<>();
        eligibleDonorList.setData(eligibleDonors);
        eligibleDonorList.setTime(stopWatch.getTotalTimeMillis());
        eligibleDonorList.setCount(eligibleDonors.size());
        RestApiResponse<ListResponse<User>> apiResponse = new RestApiResponse<>();
        apiResponse.setData(eligibleDonorList);
        return ResponseEntity.ok().body(apiResponse);
    }
    @GetMapping("/notify-near-by-donors")
    public ResponseEntity<RestApiResponse<List<User>>> NotifyNearByDonorsByEmail(@RequestBody DonorSearchRequest donorSearchRequest){
        if(donorSearchRequest.getRadius() <= 0) donorSearchRequest.setRadius(RADIUS);
        List<User> nearByUserList = donorSearchService.findNearByDonors(donorSearchRequest);
        List<User> eligibleDonors = DonorUtils.filterEligibleDonors(nearByUserList);
        notificationManager.notifyByMail(eligibleDonors, donorSearchRequest);
        log.debug("eligibleDonors : {}", nearByUserList);
        return ResponseEntity
                .ok()
                .body(RestApiResponse.success(eligibleDonors.size(),
                                "Notified " + eligibleDonors.size() + " nearby donors",
                                HttpStatus.OK));
    }


}
