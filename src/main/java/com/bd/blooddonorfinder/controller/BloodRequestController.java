package com.bd.blooddonorfinder.controller;

import com.bd.blooddonorfinder.model.enums.BloodGroup;
import com.bd.blooddonorfinder.model.enums.RequestStatus;
import com.bd.blooddonorfinder.model.enums.RequestUrgency;
import com.bd.blooddonorfinder.payload.enums.BloodRequestScope;
import com.bd.blooddonorfinder.payload.request.BloodRequestDto;
import com.bd.blooddonorfinder.payload.request.BloodRequestQuery;
import com.bd.blooddonorfinder.payload.response.BloodRequestViewDto;
import com.bd.blooddonorfinder.payload.response.PageResponse;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.security.UserPrincipal;
import com.bd.blooddonorfinder.service.BloodRequestService;
import com.bd.blooddonorfinder.utils.Utils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/blood-request")
public class BloodRequestController {
    private final BloodRequestService bloodRequestService;

    public BloodRequestController(BloodRequestService bloodRequestService) {
        this.bloodRequestService = bloodRequestService;
    }

    /**
     * Paginated request feed. {@code scope=OPEN} (default) is the donor feed of
     * unclaimed requests; {@code MINE} is the caller's own history and
     * {@code ACCEPTED} the requests they claimed as a donor. Optional filters
     * narrow the scoped set; they can never widen it.
     */
    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @GetMapping
    public ResponseEntity<RestApiResponse<PageResponse<BloodRequestViewDto>>> listRequests(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "scope", required = false, defaultValue = "OPEN")
            BloodRequestScope scope,
            @RequestParam(value = "status", required = false) RequestStatus status,
            @RequestParam(value = "bloodGroup", required = false) BloodGroup bloodGroup,
            @RequestParam(value = "urgency", required = false) RequestUrgency urgency,
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(50) int size) {
        BloodRequestQuery query = new BloodRequestQuery(scope, status, bloodGroup, urgency);
        return ResponseEntity.ok(Utils.buildSuccessRestResponse(HttpStatus.OK,
                bloodRequestService.listRequests(principal.getId(), query, page, size)));
    }

    /**
     * One request. Anyone authenticated may read an open request; claimed and
     * terminal requests are only visible to their participants.
     */
    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @GetMapping("/{id}")
    public ResponseEntity<RestApiResponse<BloodRequestViewDto>> getRequest(
            @PathVariable("id") UUID requestId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(Utils.buildSuccessRestResponse(HttpStatus.OK,
                bloodRequestService.getRequest(requestId, principal.getId())));
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
