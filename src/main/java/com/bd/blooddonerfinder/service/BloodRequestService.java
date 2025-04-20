package com.bd.blooddonerfinder.service;

import com.bd.blooddonerfinder.model.BloodRequest;
import com.bd.blooddonerfinder.payload.request.BloodRequestDto;
import com.bd.blooddonerfinder.payload.response.RestApiResponse;

public interface BloodRequestService {
    RestApiResponse<BloodRequest> createBloodRequest(BloodRequestDto bloodRequestDto);
}
