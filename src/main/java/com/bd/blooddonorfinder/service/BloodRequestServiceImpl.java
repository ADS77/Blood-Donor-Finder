package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.exception.AuthException;
import com.bd.blooddonorfinder.exception.UserNotFoundException;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.BloodRequest;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.NotificationType;
import com.bd.blooddonorfinder.model.enums.RequestStatus;
import com.bd.blooddonorfinder.model.enums.RequestUrgency;
import com.bd.blooddonorfinder.payload.request.BloodRequestDto;
import com.bd.blooddonorfinder.payload.response.BloodRequestViewDto;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.repository.BloodRequestRepository;
import com.bd.blooddonorfinder.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class BloodRequestServiceImpl implements BloodRequestService {
    private final BloodRequestRepository bloodRequestRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Value("${app.request.ttl-hours.routine:72}")
    private long routineTtlHours;
    @Value("${app.request.ttl-hours.urgent:24}")
    private long urgentTtlHours;
    @Value("${app.request.ttl-hours.critical:6}")
    private long criticalTtlHours;

    /**
     * Amplification guard: every PENDING request feeds the escalation ladder
     * (re-broadcasts to widening donor circles). Without a cap one account
     * could fan out unlimited alert waves.
     */
    @Value("${app.request.max-active-per-requester:3}")
    private int maxActiveRequestsPerRequester;

    public BloodRequestServiceImpl(BloodRequestRepository bloodRequestRepository,
                                   UserRepository userRepository,
                                   NotificationService notificationService) {
        this.bloodRequestRepository = bloodRequestRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public RestApiResponse<BloodRequestViewDto> createBloodRequest(UUID requesterId, BloodRequestDto bloodRequestDto) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Amplification guard: escalation re-broadcasts make each new request
        // cost notifications to many donors — bound the in-flight count.
        long active = bloodRequestRepository.countByRequester_IdAndStatusIn(
                requesterId,
                List.of(RequestStatus.PENDING, RequestStatus.ACCEPTED,
                        RequestStatus.PENDING_APPROVAL, RequestStatus.DISPUTED));
        if (active >= maxActiveRequestsPerRequester) {
            log.warn("Request creation blocked for requester {}: {} request(s) already in flight",
                    requesterId, active);
            throw new AuthException(ErrorCode.RATE_LIMITED,
                    "You already have " + active + " blood request(s) in flight — resolve or cancel one first");
        }

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

    /**
     * First-donor-wins claim. The chat is deliberately NOT created here: the
     * request and chat lifecycles are independent, and opening a conversation
     * is an explicit, idempotent chat-API operation. The requester is notified
     * that the request was accepted and that a chat is now available.
     */
    @Override
    @Transactional
    public RestApiResponse<BloodRequestViewDto> acceptRequest(UUID requestId, UUID donorId) {
        User donor = userRepository.findById(donorId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        if (!Boolean.TRUE.equals(donor.getIsVerified())) {
            throw new AuthException(ErrorCode.USER_NOT_VERIFIED,
                    "Only verified donors can accept requests");
        }

        BloodRequest request = bloodRequestRepository.findWithParticipantsById(requestId)
                .orElseThrow(() -> new AuthException(ErrorCode.REQUEST_NOT_FOUND, "Request not found"));
        if (request.getRequester().getId().equals(donorId)) {
            throw new AuthException(ErrorCode.INVALID_REQUEST, "You cannot accept your own request");
        }

        // Atomic conditional UPDATE: exactly one concurrent accepter wins.
        Instant acceptedAt = Instant.now();
        int accepted = bloodRequestRepository.acceptAtomically(requestId, donor, acceptedAt);
        if (accepted == 0) {
            throw new AuthException(ErrorCode.REQUEST_ALREADY_ACCEPTED,
                    "This request has already been accepted by another donor");
        }
        // The bulk update bypasses the persistence context. Mirror it on the
        // managed entity so a later full-column flush cannot write the stale
        // PENDING status back over the transition.
        request.setStatus(RequestStatus.ACCEPTED);
        request.setAcceptedDonor(donor);
        request.setAcceptedAt(acceptedAt);

        notificationService.notify(request.getRequester().getId(), NotificationType.REQUEST_ACCEPTED,
                requestId, Map.of(
                        "requestId", requestId.toString(),
                        "donorId", donor.getId().toString(),
                        "donorName", donor.getFirstName() != null ? donor.getFirstName() : "",
                        "chatAvailable", true
                ));

        log.info("Request={} accepted by donor={} — chat can now be opened on demand", requestId, donorId);
        return RestApiResponse.success(BloodRequestViewDto.from(request),
                "Request accepted — you can now start a chat");
    }

    private long ttlSeconds(RequestUrgency urgency) {
        return switch (urgency) {
            case ROUTINE -> routineTtlHours * 3600;
            case URGENT -> urgentTtlHours * 3600;
            case CRITICAL -> criticalTtlHours * 3600;
        };
    }
}
