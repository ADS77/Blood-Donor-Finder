package com.bd.blooddonorfinder.repository;

import com.bd.blooddonorfinder.model.BloodRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodRequestRepository extends JpaRepository<BloodRequest, UUID> {

    /**
     * Atomic accept: only the FIRST donor to accept a PENDING request wins.
     * Returns 1 when this caller is the winner, 0 when another donor was faster
     * or the request is no longer open.
     */
    @Modifying
    @Query("""
           UPDATE BloodRequest b
           SET b.status = com.bd.blooddonorfinder.model.enums.RequestStatus.ACCEPTED,
               b.acceptedDonor = :donor,
               b.acceptedAt = :acceptedAt
           WHERE b.id = :requestId AND b.status = com.bd.blooddonorfinder.model.enums.RequestStatus.PENDING
           """)
    int acceptAtomically(@Param("requestId") UUID requestId,
                         @Param("donor") com.bd.blooddonorfinder.model.common.User donor,
                         @Param("acceptedAt") Instant acceptedAt);

    /** Loads the requester and the accepted donor for identity checks. */
    @EntityGraph(attributePaths = {"requester", "acceptedDonor"})
    Optional<BloodRequest> findWithParticipantsById(UUID id);

    /**
     * Donor marks the donation complete: ACCEPTED → PENDING_APPROVAL, only when
     * this donor is the accepted one. Returns 1 on success, 0 otherwise.
     */
    @Modifying
    @Query("""
           UPDATE BloodRequest b
           SET b.status = com.bd.blooddonorfinder.model.enums.RequestStatus.PENDING_APPROVAL,
               b.donatedAt = :donatedAt,
               b.completionNotes = :notes,
               b.completionRequestedAt = :now
           WHERE b.id = :requestId
             AND b.status = com.bd.blooddonorfinder.model.enums.RequestStatus.ACCEPTED
             AND b.acceptedDonor.id = :donorId
           """)
    int markCompleteAtomically(@Param("requestId") UUID requestId,
                               @Param("donorId") UUID donorId,
                               @Param("donatedAt") Instant donatedAt,
                               @Param("notes") String notes,
                               @Param("now") Instant now);

    /**
     * Requester approves: PENDING_APPROVAL → FULFILLED. The conditional update
     * is the race guard — only one approval can ever transition the state, so
     * donation_record (unique per request) can never be double-written.
     */
    @Modifying
    @Query("""
           UPDATE BloodRequest b
           SET b.status = com.bd.blooddonorfinder.model.enums.RequestStatus.FULFILLED,
               b.completedAt = :now
           WHERE b.id = :requestId
             AND b.status = com.bd.blooddonorfinder.model.enums.RequestStatus.PENDING_APPROVAL
           """)
    int transitionToFulfilled(@Param("requestId") UUID requestId, @Param("now") Instant now);

    /** Requester disputes: PENDING_APPROVAL → DISPUTED. Returns 0 when raced. */
    @Modifying
    @Query("""
           UPDATE BloodRequest b
           SET b.status = com.bd.blooddonorfinder.model.enums.RequestStatus.DISPUTED,
               b.disputedAt = :now,
               b.disputeReason = :reason
           WHERE b.id = :requestId
             AND b.status = com.bd.blooddonorfinder.model.enums.RequestStatus.PENDING_APPROVAL
           """)
    int transitionToDisputed(@Param("requestId") UUID requestId,
                             @Param("reason") String reason,
                             @Param("now") Instant now);

    /** Auto-approval sweep: PENDING_APPROVAL requests whose completion claim is stale. */
    @Query("""
           SELECT b.id FROM BloodRequest b
           WHERE b.status = com.bd.blooddonorfinder.model.enums.RequestStatus.PENDING_APPROVAL
             AND b.completionRequestedAt < :cutoff
           """)
    List<UUID> findStaleCompletionIds(@Param("cutoff") Instant cutoff, Pageable pageable);

    /**
     * Bulk-expiry: every PENDING request past its TTL becomes EXPIRED in one
     * statement (cannot touch accepted/completed requests).
     */
    @Modifying
    @Query("""
           UPDATE BloodRequest b
              SET b.status = com.bd.blooddonorfinder.model.enums.RequestStatus.EXPIRED
            WHERE b.status = com.bd.blooddonorfinder.model.enums.RequestStatus.PENDING
              AND b.expiresAt < :now
           """)
    int expireStaleRequests(@Param("now") LocalDateTime now);

    /** Unanswered URGENT/CRITICAL requests whose next escalation window has arrived. */
    @Query("""
           SELECT b FROM BloodRequest b
           WHERE b.status = com.bd.blooddonorfinder.model.enums.RequestStatus.PENDING
             AND b.urgency IN (com.bd.blooddonorfinder.model.enums.RequestUrgency.URGENT,
                               com.bd.blooddonorfinder.model.enums.RequestUrgency.CRITICAL)
             AND b.escalationLevel < :maxLevel
             AND COALESCE(b.lastEscalatedAt, b.requestTime) < :cutoff
           """)
    List<BloodRequest> findEscalationCandidates(@Param("cutoff") LocalDateTime cutoff,
                                                @Param("maxLevel") int maxLevel, Pageable pageable);

    /** Advances the escalation marker (idempotent per sweep via ShedLock). */
    @Modifying
    @Query("""
           UPDATE BloodRequest b
              SET b.escalationLevel = b.escalationLevel + 1,
                  b.lastEscalatedAt = :now,
                  b.alertedUserIds = :alertedIds
            WHERE b.id = :requestId
           """)
    int applyEscalation(@Param("requestId") UUID requestId,
                        @Param("alertedIds") List<String> alertedIds,
                        @Param("now") LocalDateTime now);
}
