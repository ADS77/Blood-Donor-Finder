package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.exception.UserNotFoundException;
import com.bd.blooddonorfinder.model.BloodRequest;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.RequestStatus;
import com.bd.blooddonorfinder.model.enums.RequestUrgency;
import com.bd.blooddonorfinder.payload.request.BloodRequestDto;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.repository.BloodRequestRepository;
import com.bd.blooddonorfinder.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.request.ttl-hours.routine:72}")
    private long routineTtlHours;
    @Value("${app.request.ttl-hours.urgent:24}")
    private long urgentTtlHours;
    @Value("${app.request.ttl-hours.critical:6}")
    private long criticalTtlHours;

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
        // Urgency drives the auto-expiry TTL (and the alert escalation cadence).
        RequestUrgency urgency = bloodRequestDto.getUrgency() != null
                ? bloodRequestDto.getUrgency() : RequestUrgency.ROUTINE;
        bloodRequest.setUrgency(urgency);
        bloodRequest.setExpiresAt(LocalDateTime.now().plusSeconds(ttlSeconds(urgency)));

        BloodRequest saved = bloodRequestRepository.save(bloodRequest);
        log.debug("Blood request {} created by userId={}, urgency={}, expiresAt={}",
                saved.getId(), requesterId, urgency, saved.getExpiresAt());

        return RestApiResponse.success(saved, "Blood Request Created");
    }

    private long ttlSeconds(RequestUrgency urgency) {
        return switch (urgency) {
            case ROUTINE -> routineTtlHours * 3600;
            case URGENT -> urgentTtlHours * 3600;
            case CRITICAL -> criticalTtlHours * 3600;
        };
    }
}
