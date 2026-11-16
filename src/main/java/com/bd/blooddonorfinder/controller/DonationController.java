package com.bd.blooddonorfinder.controller;

import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.payload.request.DonationApprovalRequest;
import com.bd.blooddonorfinder.payload.request.DonationCompleteRequest;
import com.bd.blooddonorfinder.payload.request.DonationDisputeRequest;
import com.bd.blooddonorfinder.payload.response.DonationDto;
import com.bd.blooddonorfinder.payload.response.DonorStatsDto;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.security.AppPermissions;
import com.bd.blooddonorfinder.security.UserPrincipal;
import com.bd.blooddonorfinder.service.DonationService;
import com.bd.blooddonorfinder.utils.Utils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Donation completion flow. After a donor accepts a blood request the
 * donation itself is tracked here:
 * <pre>
 *   donor:  POST /{requestId}/complete   → PENDING_APPROVAL
 *   user:   POST /{requestId}/approve    → FULFILLED (count + rating update)
 *   user:   POST /{requestId}/dispute    → DISPUTED
 * </pre>
 */
@RestController
@RequestMapping("/api/donations")
@Slf4j
public class DonationController {

    private final DonationService donationService;

    public DonationController(DonationService donationService) {
        this.donationService = donationService;
    }

    /** Donor (the accepted one) reports the donation as done. */
    @PreAuthorize("@authz.hasAnyRole(authentication, '" + AppPermissions.ROLE_DONOR + "')")
    @PostMapping("/{requestId}/complete")
    public ResponseEntity<RestApiResponse<DonationDto>> markComplete(
            @PathVariable UUID requestId,
            @Valid @RequestBody(required = false) DonationCompleteRequest body,
            Authentication authentication) {
        UUID callerId = requireCaller(authentication);
        return ResponseEntity.ok(Utils.buildSuccessRestResponse(HttpStatus.OK,
                "Donation marked complete — awaiting the requester's approval",
                donationService.markComplete(callerId, requestId, body)));
    }

    /** Requester approves the donation (optionally rates 1–5). */
    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @PostMapping("/{requestId}/approve")
    public ResponseEntity<RestApiResponse<DonationDto>> approve(
            @PathVariable UUID requestId,
            @Valid @RequestBody(required = false) DonationApprovalRequest body,
            Authentication authentication) {
        UUID callerId = requireCaller(authentication);
        return ResponseEntity.ok(Utils.buildSuccessRestResponse(HttpStatus.OK,
                "Donation approved — donor's record updated",
                donationService.approve(callerId, requestId, body)));
    }

    /** Requester rejects the completion claim (reason required). */
    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @PostMapping("/{requestId}/dispute")
    public ResponseEntity<RestApiResponse<DonationDto>> dispute(
            @PathVariable UUID requestId,
            @Valid @RequestBody DonationDisputeRequest body,
            Authentication authentication) {
        UUID callerId = requireCaller(authentication);
        return ResponseEntity.ok(Utils.buildSuccessRestResponse(HttpStatus.OK,
                "Completion claim disputed",
                donationService.dispute(callerId, requestId, body)));
    }

    /** Lifetime stats for any donor (count, average rating). */
    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @GetMapping("/stats/{donorId}")
    public ResponseEntity<RestApiResponse<DonorStatsDto>> stats(
            @PathVariable UUID donorId, Authentication authentication) {
        return ResponseEntity.ok(Utils.buildSuccessRestResponse(HttpStatus.OK,
                donationService.getStats(donorId)));
    }

    /** The caller's own donation history, newest first (keyset via {@code before}). */
    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @GetMapping("/my")
    public ResponseEntity<RestApiResponse<List<DonationDto>>> myHistory(
            Authentication authentication,
            @RequestParam(value = "before", required = false)
            @Min(value = 0, message = "before must be a positive epoch-millis") Long before,
            @RequestParam(value = "size", required = false) @Min(1) @Max(100) Integer size) {
        UUID callerId = requireCaller(authentication);
        return ResponseEntity.ok(Utils.buildSuccessRestResponse(HttpStatus.OK,
                donationService.myHistory(callerId, before, size)));
    }

    private static UUID requireCaller(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        throw new com.bd.blooddonorfinder.exception.AuthException(
                ErrorCode.UNAUTHORIZED, "Authentication required");
    }
}
