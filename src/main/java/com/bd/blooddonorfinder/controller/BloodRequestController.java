package com.bd.blooddonorfinder.controller;

import com.bd.blooddonorfinder.model.BloodRequest;
import com.bd.blooddonorfinder.payload.request.BloodRequestDto;
import com.bd.blooddonorfinder.payload.response.AcceptRequestResponse;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.security.UserPrincipal;
import com.bd.blooddonorfinder.service.BloodRequestService;
import com.bd.blooddonorfinder.service.ChatService;
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
    private final ChatService chatService;

    public BloodRequestController(BloodRequestService bloodRequestService, ChatService chatService) {
        this.bloodRequestService = bloodRequestService;
        this.chatService = chatService;
    }

    @PostMapping("/create")
    public ResponseEntity<RestApiResponse<BloodRequest>> createBloodRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BloodRequestDto bloodRequestDto) {
        return ResponseEntity.ok()
                .body(bloodRequestService.createBloodRequest(principal.getId(), bloodRequestDto));
    }

    /**
     * Donor accepts a blood request. Exactly one donor can win (atomic, first-come);
     * acceptance opens the private chat between requester and this donor.
     */
    @PreAuthorize("hasAnyRole('DONOR','ORG_ADMIN','SYSTEM_ADMIN')")
    @PostMapping("/{id}/accept")
    public ResponseEntity<RestApiResponse<AcceptRequestResponse>> acceptRequest(
            @PathVariable("id") UUID requestId,
            @AuthenticationPrincipal UserPrincipal principal) {
        AcceptRequestResponse response = chatService.acceptRequest(requestId, principal.getId());
        return ResponseEntity.ok()
                .body(RestApiResponse.success(response, "Request accepted — chat opened"));
    }
}
