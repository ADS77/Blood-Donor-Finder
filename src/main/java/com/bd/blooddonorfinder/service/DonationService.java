package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.payload.request.DonationApprovalRequest;
import com.bd.blooddonorfinder.payload.request.DonationCompleteRequest;
import com.bd.blooddonorfinder.payload.request.DonationDisputeRequest;
import com.bd.blooddonorfinder.payload.response.DonationDto;
import com.bd.blooddonorfinder.payload.response.DonorStatsDto;

import java.util.List;
import java.util.UUID;

/**
 * Donation completion flow:
 * <pre>
 *   ACCEPTED ──(donor completes)──► PENDING_APPROVAL ──(requester approves)──► FULFILLED
 *                                            └──(requester disputes)──► DISPUTED
 * </pre>
 * Approval writes an immutable donation_record and bumps the donor's
 * donation count and rating in the same transaction.
 */
public interface DonationService {

    /** Donor (the accepted one) marks the donation as done → PENDING_APPROVAL. */
    DonationDto markComplete(UUID donorId, UUID requestId, DonationCompleteRequest request);

    /** Requester approves (optionally rates) → FULFILLED + counters + close chat. */
    DonationDto approve(UUID requesterId, UUID requestId, DonationApprovalRequest request);

    /** Requester rejects the completion claim → DISPUTED, donor is notified. */
    DonationDto dispute(UUID requesterId, UUID requestId, DonationDisputeRequest request);

    /** Lifetime stats for a donor profile. */
    DonorStatsDto getStats(UUID donorId);

    /** The caller's own donation history, newest first (keyset). */
    List<DonationDto> myHistory(UUID donorId, Long beforeEpochMs, Integer size);

    /** System path (worker): auto-approve a stale completion claim, no rating. */
    void autoApprove(UUID requestId);

    /**
     * Admin adjudication of a DISPUTED completion claim. {@code approve=true}
     * grants the donation (counters + record + chat close); {@code false}
     * cancels the request. Both notify the donor.
     */
    DonationDto adminResolve(UUID requestId, boolean approve, String note);
}
