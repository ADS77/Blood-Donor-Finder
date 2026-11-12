package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.exception.UserNotFoundException;
import com.bd.blooddonorfinder.model.BloodRequest;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.RequestStatus;
import com.bd.blooddonorfinder.payload.request.BloodRequestDto;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.repository.BloodRequestRepository;
import com.bd.blooddonorfinder.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class BloodRequestServiceImpl implements BloodRequestService {
    private final BloodRequestRepository bloodRequestRepository;
    private final UserRepository userRepository;

    public BloodRequestServiceImpl(BloodRequestRepository bloodRequestRepository, UserRepository userRepository) {
        this.bloodRequestRepository = bloodRequestRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public RestApiResponse<BloodRequest> createBloodRequest(UUID requesterId, BloodRequestDto bloodRequestDto) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        BloodRequest bloodRequest = new BloodRequest();
        bloodRequest.setRequester(requester);
        bloodRequest.setBloodGroup(bloodRequestDto.getNeededBloodGroup());
        bloodRequest.setGeoLocation(bloodRequestDto.getGeoLocation());
        bloodRequest.setRequiredQuantity(bloodRequestDto.getQuantity());
        bloodRequest.setStatus(RequestStatus.PENDING);
        bloodRequest.setRequestTime(LocalDateTime.now());
        bloodRequest.setMessage(bloodRequestDto.getMessage());

        BloodRequest saved = bloodRequestRepository.save(bloodRequest);
        log.debug("Blood request {} created by userId={}", saved.getId(), requesterId);

        return RestApiResponse.success(saved, "Blood Request Created");
    }
}
