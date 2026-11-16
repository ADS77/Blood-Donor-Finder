package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.exception.AuthException;
import com.bd.blooddonorfinder.exception.UserNotFoundException;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.BloodRequest;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.RequestStatus;
import com.bd.blooddonorfinder.model.enums.RequestUrgency;
import com.bd.blooddonorfinder.payload.request.BloodRequestDto;
import com.bd.blooddonorfinder.payload.response.BloodRequestViewDto;
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
    public RestApiResponse<BloodRequestViewDto> createBloodRequest(UUID requesterId, BloodRequestDto bloodRequestDto) {
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

        return RestApiResponse.success(BloodRequestViewDto.from(saved), "Blood Request Created");
    }

    /**
     * Requester cancels their own pending request. Cancellation is only legal
     * before a donor accepts — afterwards the flow belongs to the donation
     * lifecycle (complete → approve/dispute). Same ownership guard as the
     * donation flow: other people's requests read as "not found" so request
     * ids cannot be enumerated.
     */
    @Override
    @Transactional
    public RestApiResponse<BloodRequestViewDto> cancelRequest(UUID requesterId, UUID requestId) {
        BloodRequest request = bloodRequestRepository.findWithParticipantsById(requestId)
                .orElseThrow(() -> new AuthException(ErrorCode.REQUEST_NOT_FOUND, "Request not found"));
        if (!requesterId.equals(request.getRequester().getId())) {
            log.warn("User={} attempted to cancel request={} they did not create", requesterId, requestId);
            throw new AuthException(ErrorCode.REQUEST_NOT_FOUND, "Request not found");
        }

        // Conditional transition — the race guard against a donor accepting
        // while the requester cancels; only one of the two can win.
        int moved = bloodRequestRepository.cancelAtomically(requestId, requesterId);
        if (moved == 0) {
            log.debug("Cannot cancel request {} in state {}", requestId, request.getStatus());
            throw new AuthException(ErrorCode.INVALID_REQUEST,
                    "Request cannot be cancelled from state " + request.getStatus());
        }
        // Mirror the bulk update on the managed entity so no later mutation
        // flushes the stale PENDING status back (full-column flush hazard).
        request.setStatus(RequestStatus.CANCELLED);
        log.info("Request {} cancelled by requester {}", requestId, requesterId);

        return RestApiResponse.success(BloodRequestViewDto.from(request), "Request cancelled");
    }

    private long ttlSeconds(RequestUrgency urgency) {
        return switch (urgency) {
            case ROUTINE -> routineTtlHours * 3600;
            case URGENT -> urgentTtlHours * 3600;
            case CRITICAL -> criticalTtlHours * 3600;
        };
    }
}
