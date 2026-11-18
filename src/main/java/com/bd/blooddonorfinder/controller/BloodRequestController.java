package com.bd.blooddonorfinder.controller;

import com.bd.blooddonorfinder.payload.request.BloodRequestDto;
import com.bd.blooddonorfinder.payload.response.BloodRequestViewDto;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.security.UserPrincipal;
import com.bd.blooddonorfinder.service.BloodRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/blood-request")
public class BloodRequestController {
    private final BloodRequestService bloodRequestService;

    public BloodRequestController(BloodRequestService bloodRequestService) {
        this.bloodRequestService = bloodRequestService;
    }

    @PostMapping("/create")
    public ResponseEntity<RestApiResponse<BloodRequestViewDto>> createBloodRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BloodRequestDto bloodRequestDto) {
        return ResponseEntity.ok()
                .body(bloodRequestService.createBloodRequest(principal.getId(), bloodRequestDto));
    }

    /**
     * Requester cancels their own pending request. No donor is attached yet in
     * PENDING, so there is nothing to notify — the request simply stops being
     * visible to donors.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<RestApiResponse<BloodRequestViewDto>> cancelRequest(
            @PathVariable("id") UUID requestId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok()
                .body(bloodRequestService.cancelRequest(principal.getId(), requestId));
    }

    /**
     * Donor accepts a blood request. Exactly one donor can win (atomic,
     * first-come). Acceptance only claims the request: the UI shows a chat
     * option from {@code chatAvailable}, and either participant calls the chat
     * API to open the conversation when they actually want to talk.
     */
    @PreAuthorize("hasAnyRole('DONOR','ORG_ADMIN','SYSTEM_ADMIN')")
    @PostMapping("/{id}/accept")
    public ResponseEntity<RestApiResponse<BloodRequestViewDto>> acceptRequest(
            @PathVariable("id") UUID requestId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok()
                .body(bloodRequestService.acceptRequest(requestId, principal.getId()));
    }
}
