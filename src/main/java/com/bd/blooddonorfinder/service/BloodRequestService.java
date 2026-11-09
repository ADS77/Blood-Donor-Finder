package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.model.BloodRequest;
import com.bd.blooddonorfinder.payload.request.BloodRequestDto;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;

public interface BloodRequestService {
    RestApiResponse<BloodRequest> createBloodRequest(BloodRequestDto bloodRequestDto);
}
