package com.bd.blooddonerfinder.controller;

import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.payload.request.DonorSearchRequest;
import com.bd.blooddonerfinder.payload.response.RestApiResponse;
import com.bd.blooddonerfinder.service.DonorSearchService;
import com.bd.blooddonerfinder.service.NotificationManager;
import com.bd.blooddonerfinder.util.MailUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/search")
@Slf4j
public class DonorSearchController {
    private final long RADIUS = 10;
    private final DonorSearchService donorSearchService;
    private final NotificationManager notificationManager;

    public DonorSearchController(DonorSearchService donorSearchService, NotificationManager notificationManager) {
        this.donorSearchService = donorSearchService;
        this.notificationManager = notificationManager;
    }
    @GetMapping("/notify-near-by-donors")
    public ResponseEntity<RestApiResponse<List<User>>> NotifyNearByDonorsByEmail(@RequestBody DonorSearchRequest donorSearchRequest){
        if(donorSearchRequest.getRadius() <= 0) donorSearchRequest.setRadius(RADIUS);
        List<User> nearByUserList = donorSearchService.findNearByDonors(donorSearchRequest);
        List<User> eligibleDonors = MailUtils.filterEligibleDonors(nearByUserList);
        notificationManager.notifyByMail(eligibleDonors, donorSearchRequest);
        log.debug("eligibleDonors : {}", nearByUserList);
        return ResponseEntity
                .ok()
                .body(RestApiResponse.success(eligibleDonors.size(),
                                "Notified " + eligibleDonors.size() + " nearby donors",
                                HttpStatus.OK));
    }


}
