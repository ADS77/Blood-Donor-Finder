package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.payload.request.BloodRequestDto;
import com.bd.blooddonorfinder.payload.request.BloodRequestQuery;
import com.bd.blooddonorfinder.payload.response.BloodRequestViewDto;
import com.bd.blooddonorfinder.payload.response.PageResponse;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;

import java.util.UUID;

public interface BloodRequestService {
    RestApiResponse<BloodRequestViewDto> createBloodRequest(UUID requesterId, BloodRequestDto bloodRequestDto);

    /**
     * Paginated read model, scoped to what the caller may see:
     * {@code OPEN} exposes PENDING requests, {@code MINE} the caller's own
     * requests, {@code ACCEPTED} the requests the caller claimed as a donor.
     * Optional status/blood-group/urgency filters narrow the scoped set.
     */
    PageResponse<BloodRequestViewDto> listRequests(UUID callerId, BloodRequestQuery query, int page, int size);

    /**
     * One request. Participants always see it; everyone else only while it is
     * still PENDING. Claimed or terminal requests read as "not found" so their
     * ids cannot be used to enumerate other people's history.
     */
    BloodRequestViewDto getRequest(UUID requestId, UUID callerId);

    /**
     * Requester cancels their own request: PENDING → CANCELLED. Ownership and
     * state guards live in the implementation; raced or wrong-state cancels
     * are rejected.
     */
    RestApiResponse<BloodRequestViewDto> cancelRequest(UUID requesterId, UUID requestId);

    /**
     * A verified donor claims a PENDING request: PENDING → ACCEPTED, first
     * donor wins. This transition is complete on its own — it does not create
     * a chat. Both participants may open the conversation later through the
     * chat API, any time the request status {@link com.bd.blooddonorfinder.model.enums.RequestStatus#allowsChat() allows it}.
     */
    RestApiResponse<BloodRequestViewDto> acceptRequest(UUID requestId, UUID donorId);
}
