package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.model.BloodRequest;
import com.bd.blooddonorfinder.payload.request.BloodRequestDto;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;

import java.util.UUID;

public interface BloodRequestService {
    RestApiResponse<BloodRequest> createBloodRequest(UUID requesterId, BloodRequestDto bloodRequestDto);
}
