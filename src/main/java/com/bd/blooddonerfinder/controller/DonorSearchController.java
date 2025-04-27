package com.bd.blooddonerfinder.controller;

import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.model.enums.BloodGroup;
import com.bd.blooddonerfinder.payload.request.DonorSearchRequest;
import com.bd.blooddonerfinder.payload.response.RestApiResponse;
import com.bd.blooddonerfinder.service.DonorSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/donors")
@Slf4j
public class DonorSearchController {
    private final long RADIUS = 10;
    private final DonorSearchService donorSearchService;

    public DonorSearchController(DonorSearchService donorSearchService) {
        this.donorSearchService = donorSearchService;
    }
    @GetMapping("/near-by-donors")
    public ResponseEntity<RestApiResponse<List<User>>> getNearByDonors(@RequestBody DonorSearchRequest donorSearchRequest){
        if(donorSearchRequest.getRadius() <= 0) donorSearchRequest.setRadius(RADIUS);
        List<User> nearByUserList = donorSearchService.findNearByDonors(donorSearchRequest);
        log.debug("nearByUserList : {}", nearByUserList);
        return ResponseEntity.ok()
                .body(RestApiResponse.success(Collections.singletonList(nearByUserList),"Fetched nearby user list successfully"));
    }


}
