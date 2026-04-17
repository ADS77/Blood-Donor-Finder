package com.bd.blooddonorfinder.controller;

import com.bd.blooddonorfinder.model.BloodRequest;
import com.bd.blooddonorfinder.payload.request.BloodRequestDto;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.service.BloodRequestServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/blood-request")
public class BloodRequestController {
    private final BloodRequestServiceImpl bloodRequestService;

    public BloodRequestController(BloodRequestServiceImpl bloodRequestService) {
        this.bloodRequestService = bloodRequestService;
    }

    @PostMapping("/create")
    public ResponseEntity<RestApiResponse<BloodRequest>> createBloodRequest(@RequestBody BloodRequestDto bloodRequestDto){
        return ResponseEntity.ok().body(bloodRequestService.createBloodRequest(bloodRequestDto));
    }
}
