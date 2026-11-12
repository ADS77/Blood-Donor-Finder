package com.bd.blooddonorfinder.controller;

import com.bd.blooddonorfinder.model.BloodRequest;
import com.bd.blooddonorfinder.payload.request.BloodRequestDto;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.security.UserPrincipal;
import com.bd.blooddonorfinder.service.BloodRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public ResponseEntity<RestApiResponse<BloodRequest>> createBloodRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BloodRequestDto bloodRequestDto) {
        return ResponseEntity.ok()
                .body(bloodRequestService.createBloodRequest(principal.getId(), bloodRequestDto));
    }
}
