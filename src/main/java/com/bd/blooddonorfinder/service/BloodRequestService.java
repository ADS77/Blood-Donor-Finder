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
}
