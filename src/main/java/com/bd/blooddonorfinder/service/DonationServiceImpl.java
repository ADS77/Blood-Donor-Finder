package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.exception.AuthException;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.BloodRequest;
import com.bd.blooddonorfinder.model.donation.DonationRecord;
import com.bd.blooddonorfinder.model.enums.NotificationType;
import com.bd.blooddonorfinder.model.enums.RequestStatus;
import com.bd.blooddonorfinder.payload.request.DonationApprovalRequest;
import com.bd.blooddonorfinder.payload.request.DonationCompleteRequest;
import com.bd.blooddonorfinder.payload.request.DonationDisputeRequest;
import com.bd.blooddonorfinder.payload.response.DonationDto;
import com.bd.blooddonorfinder.payload.response.DonorStatsDto;
import com.bd.blooddonorfinder.repository.BloodRequestRepository;
import com.bd.blooddonorfinder.repository.DonationRecordRepository;
import com.bd.blooddonorfinder.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Race-safety: every state transition is a conditional UPDATE (only wins from
 * the exact source state), the same pattern as the atomic accept. A concurrent
 * double-approve loses the transition and gets a clean error — the unique
 * constraint on donation_record.blood_request_id is the second net.
 */
@Service
@Slf4j
public class DonationServiceImpl implements DonationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final long FUTURE_TOLERANCE_SECONDS = 300;

    private final BloodRequestRepository bloodRequestRepository;
    private final DonationRecordRepository donationRecordRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;
    private final NotificationService notificationService;
    private final long futureToleranceSeconds;

    public DonationServiceImpl(BloodRequestRepository bloodRequestRepository,
                               DonationRecordRepository donationRecordRepository,
                               UserRepository userRepository,
                               ChatService chatService,
                               NotificationService notificationService,
                               @Value("${app.donation.future-tolerance-seconds:300}") long futureToleranceSeconds) {
        this.bloodRequestRepository = bloodRequestRepository;
        this.donationRecordRepository = donationRecordRepository;
        this.userRepository = userRepository;
        this.chatService = chatService;
        this.notificationService = notificationService;
        this.futureToleranceSeconds = futureToleranceSeconds;
    }

    // ── donor: mark donation complete ───────────────────────────────────

    @Override
    @Transactional
    public DonationDto markComplete(UUID donorId, UUID requestId, DonationCompleteRequest body) {
        BloodRequest request = loadForDonor(donorId, requestId);

        Instant donatedAt = body != null && body.getDonatedAt() != null
                ? body.getDonatedAt() : Instant.now();
        if (donatedAt.isAfter(Instant.now().plusSeconds(futureToleranceSeconds))) {
            throw new AuthException(ErrorCode.INVALID_REQUEST, "donatedAt cannot be in the future");
        }
        String notes = body != null ? body.getNotes() : null;

        // conditional transition — winner is the accepted donor acting on ACCEPTED state
        int moved = bloodRequestRepository.markCompleteAtomically(requestId, donorId, donatedAt, notes, Instant.now());
        if (moved == 0) {
            throw wrongState(request.getStatus(), "complete");
        }
        log.info("Donor={} marked request={} complete (donatedAt={})", donorId, requestId, donatedAt);

        // post-commit: badge + feed entry for the requester
        notificationService.notify(request.getRequester().getId(), NotificationType.DONATION_COMPLETED,
                requestId, Map.of(
                        "requestId", requestId.toString(),
                        "donorId", donorId.toString(),
                        "donatedAt", donatedAt.toEpochMilli(),
                        "notes", notes != null ? notes : ""));
        return toDto(requestId, request, donatedAt, null, null);
    }

    // ── requester: approve / dispute ────────────────────────────────────

    @Override
    @Transactional
    public DonationDto approve(UUID requesterId, UUID requestId, DonationApprovalRequest body) {
        BloodRequest request = loadForRequester(requesterId, requestId);
        if (request.getStatus() != RequestStatus.PENDING_APPROVAL) {
            throw wrongState(request.getStatus(), "approve");
        }

        int moved = bloodRequestRepository.transitionToFulfilled(requestId, Instant.now());
        if (moved == 0) {
            // raced with another approval/dispute — re-read the authoritative state
            throw wrongState(bloodRequestRepository.findById(requestId)
                    .map(BloodRequest::getStatus).orElse(RequestStatus.CANCELLED), "approve");
        }

        Instant donatedAt = request.getDonatedAt() != null ? request.getDonatedAt() : Instant.now();
        Integer rating = body != null ? body.getRating() : null;
        String review = body != null ? body.getReview() : null;

        if (rating != null) {
            userRepository.recordDonationRated(request.getAcceptedDonor().getId(),
                    toLocalDateTime(donatedAt), rating);
        } else {
            userRepository.recordDonation(request.getAcceptedDonor().getId(),
                    toLocalDateTime(donatedAt));
        }

        DonationRecord record = new DonationRecord();
        record.setRequest(request);
        record.setDonor(request.getAcceptedDonor());
        record.setRequester(request.getRequester());
        record.setBloodGroup(request.getBloodGroup());
        record.setDonatedAt(donatedAt);
        record.setRating(rating);
        record.setReview(review);
        record = donationRecordRepository.save(record);

        chatService.closeChatForRequest(requestId);

        log.info("Request={} donation approved by requester={} (rating={})", requestId, requesterId, rating);

        Map<String, Object> payload = Map.of(
                "requestId", requestId.toString(),
                "rating", rating != null ? rating : 0,
                "totalDonations", request.getAcceptedDonor().getTotalDonations() != null
                        ? request.getAcceptedDonor().getTotalDonations() + 1 : 1);
        notificationService.notify(request.getAcceptedDonor().getId(),
                NotificationType.DONATION_APPROVED, record.getId(), payload);

        return toDto(requestId, request, donatedAt, rating, review);
    }

    @Override
    @Transactional
    public DonationDto dispute(UUID requesterId, UUID requestId, DonationDisputeRequest body) {
        BloodRequest request = loadForRequester(requesterId, requestId);
        if (request.getStatus() != RequestStatus.PENDING_APPROVAL) {
            throw wrongState(request.getStatus(), "dispute");
        }
        String reason = body.getReason();

        int moved = bloodRequestRepository.transitionToDisputed(requestId, reason, Instant.now());
        if (moved == 0) {
            throw wrongState(bloodRequestRepository.findById(requestId)
                    .map(BloodRequest::getStatus).orElse(RequestStatus.CANCELLED), "dispute");
        }
        log.warn("Request={} completion disputed by requester={}", requestId, requesterId);

        notificationService.notify(request.getAcceptedDonor().getId(),
                NotificationType.DONATION_DISPUTED, requestId, Map.of(
                        "requestId", requestId.toString(),
                        "reason", reason));

        return toDto(requestId, request, request.getDonatedAt(), null, null);
    }

    // ── queries ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public DonorStatsDto getStats(UUID donorId) {
        var user = userRepository.findById(donorId)
                .orElseThrow(() -> new AuthException(ErrorCode.USER_NOT_FOUND, "User not found"));
        long ratingCount = user.getRatingCount() != null ? user.getRatingCount() : 0;
        double rating = user.getRating() != null ? user.getRating() : 0.0;
        return new DonorStatsDto(donorId, user.getTotalDonations(), rating, ratingCount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DonationDto> myHistory(UUID donorId, Long beforeEpochMs, Integer size) {
        int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : 30;
        PageRequest page = PageRequest.of(0, pageSize);
        List<DonationRecord> history = beforeEpochMs == null
                ? donationRecordRepository.findDonorHistory(donorId, page)
                : donationRecordRepository.findDonorHistoryBefore(
                        donorId, Instant.ofEpochMilli(beforeEpochMs), page);
        return history.stream().map(this::toDto).toList();
    }

    // ── system: auto-approve stale claims ───────────────────────────────

    @Override
    @Transactional
    public void autoApprove(UUID requestId) {
        int moved = bloodRequestRepository.transitionToFulfilled(requestId, Instant.now());
        if (moved == 0) {
            return; // raced or already resolved — nothing to do
        }
        completeDonationSystemPath(requestId, Map.of(
                "requestId", requestId.toString(),
                "autoConfirmed", true));
    }

    @Override
    @Transactional
    public DonationDto adminResolve(UUID requestId, boolean approve, String note) {
        int moved = approve
                ? bloodRequestRepository.adminResolveDisputedToApproved(requestId, note, Instant.now())
                : bloodRequestRepository.adminResolveDisputedToCancelled(requestId, note, Instant.now());
        if (moved == 0) {
            BloodRequest current = bloodRequestRepository.findById(requestId).orElse(null);
            if (current == null) {
                throw new AuthException(ErrorCode.REQUEST_NOT_FOUND, "Request not found");
            }
            throw wrongState(current.getStatus(), "resolve");
        }
        BloodRequest request = bloodRequestRepository.findWithParticipantsById(requestId).orElse(null);
        if (request == null || request.getAcceptedDonor() == null) {
            throw new AuthException(ErrorCode.REQUEST_NOT_FOUND, "Request not found");
        }
        if (approve) {
            completeDonationSystemPath(requestId, Map.of(
                    "requestId", requestId.toString(),
                    "adminResolved", true,
                    "note", note != null ? note : ""));
        } else {
            notificationService.notify(request.getAcceptedDonor().getId(),
                    NotificationType.DONATION_DISPUTED, requestId, Map.of(
                            "requestId", requestId.toString(),
                            "resolution", "REJECTED",
                            "note", note != null ? note : ""));
        }
        log.info("Admin resolved dispute for request={}: {} (note={})", requestId,
                approve ? "APPROVED" : "REJECTED", note);
        Instant donatedAt = request.getDonatedAt() != null ? request.getDonatedAt() : Instant.now();
        return toDto(requestId, request, donatedAt, null, null);
    }

    /**
     * Shared tail of every system-side approval: donation_record, donor
     * counters, chat close, donor notification. Caller must have already
     * transitioned the request to FULFILLED.
     */
    private void completeDonationSystemPath(UUID requestId, Map<String, Object> payload) {
        BloodRequest request = bloodRequestRepository.findWithParticipantsById(requestId).orElse(null);
        if (request == null || request.getAcceptedDonor() == null) {
            return;
        }
        Instant donatedAt = request.getDonatedAt() != null ? request.getDonatedAt() : Instant.now();

        userRepository.recordDonation(request.getAcceptedDonor().getId(), toLocalDateTime(donatedAt));

        DonationRecord record = new DonationRecord();
        record.setRequest(request);
        record.setDonor(request.getAcceptedDonor());
        record.setRequester(request.getRequester());
        record.setBloodGroup(request.getBloodGroup());
        record.setDonatedAt(donatedAt);
        record = donationRecordRepository.save(record);

        chatService.closeChatForRequest(requestId);

        notificationService.notify(request.getAcceptedDonor().getId(),
                NotificationType.DONATION_APPROVED, record.getId(), payload);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private BloodRequest loadForDonor(UUID donorId, UUID requestId) {
        BloodRequest request = bloodRequestRepository.findWithParticipantsById(requestId)
                .orElseThrow(() -> new AuthException(ErrorCode.REQUEST_NOT_FOUND, "Request not found"));
        UUID acceptedDonorId = request.getAcceptedDonor() != null ? request.getAcceptedDonor().getId() : null;
        if (!donorId.equals(acceptedDonorId)) {
            // silent 404 — never reveal another donor's claim
            log.warn("User={} attempted to complete request={} they did not accept", donorId, requestId);
            throw new AuthException(ErrorCode.REQUEST_NOT_FOUND, "Request not found");
        }
        return request;
    }

    private BloodRequest loadForRequester(UUID requesterId, UUID requestId) {
        BloodRequest request = bloodRequestRepository.findWithParticipantsById(requestId)
                .orElseThrow(() -> new AuthException(ErrorCode.REQUEST_NOT_FOUND, "Request not found"));
        if (!requesterId.equals(request.getRequester().getId())) {
            log.warn("User={} attempted to approve request={} they did not create", requesterId, requestId);
            throw new AuthException(ErrorCode.REQUEST_NOT_FOUND, "Request not found");
        }
        return request;
    }

    private AuthException wrongState(RequestStatus current, String action) {
        log.debug("Cannot {} request in state {}", action, current);
        return new AuthException(ErrorCode.INVALID_REQUEST,
                "Donation cannot be " + action + "d from state " + current);
    }

    private DonationDto toDto(UUID requestId, BloodRequest request, Instant donatedAt,
                              Integer rating, String review) {
        return new DonationDto(
                requestId,
                requestId,
                request.getAcceptedDonor() != null ? request.getAcceptedDonor().getId() : null,
                request.getRequester().getId(),
                request.getBloodGroup().name(),
                donatedAt != null ? donatedAt.toEpochMilli() : 0L,
                rating,
                review);
    }

    private DonationDto toDto(DonationRecord record) {
        return new DonationDto(
                record.getId(),
                record.getRequest().getId(),
                record.getDonor().getId(),
                record.getRequester().getId(),
                record.getBloodGroup().name(),
                record.getDonatedAt().toEpochMilli(),
                record.getRating(),
                record.getReview());
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
