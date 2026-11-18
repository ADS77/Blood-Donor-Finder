package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.payload.request.BloodRequestDto;
import com.bd.blooddonorfinder.payload.response.BloodRequestViewDto;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;

import java.util.UUID;

public interface BloodRequestService {
    RestApiResponse<BloodRequestViewDto> createBloodRequest(UUID requesterId, BloodRequestDto bloodRequestDto);

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
