package com.bd.blooddonerfinder.controller;

import com.bd.blooddonerfinder.model.BloodRequest;
import com.bd.blooddonerfinder.payload.request.BloodRequestDto;
import com.bd.blooddonerfinder.payload.response.RestApiResponse;
import com.bd.blooddonerfinder.service.BloodRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/blood-request")
public class BloodRequestController {
    private final BloodRequestService bloodRequestService;

    public BloodRequestController(BloodRequestService bloodRequestService) {
        this.bloodRequestService = bloodRequestService;
    }

    @PostMapping("/create")
    public ResponseEntity<RestApiResponse<BloodRequest>> createBloodRequest(@RequestBody BloodRequestDto bloodRequestDto){
        return ResponseEntity.ok().body(bloodRequestService.createBloodRequest(bloodRequestDto));
    }
}
