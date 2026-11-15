package com.bd.blooddonorfinder.repository;

import com.bd.blooddonorfinder.model.PiiToken;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.BloodGroup;
import com.bd.blooddonorfinder.model.enums.GeoStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByPhoneToken(PiiToken phoneToken);
    Optional<User>findByEmailToken(PiiToken emailToken);
    Optional<User>findByFirstName(String firstName);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findUserWithRolesAndPermissionsByPhoneToken(PiiToken phoneToken);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findUserWithRolesAndPermissionsByEmailToken(PiiToken emailToken);
    boolean existsByEmailToken(PiiToken emailToken);
    boolean existsByPhoneToken(PiiToken phoneToken);

    List<User> findNearByAndBloodGroupAndGeoLocationCity(BloodGroup bloodGroup, String city);
    List<User> findByGeoLocation_GeoStatusOrderByCreatedAtAsc(GeoStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u set u.geoLocation.geoStatus = 'PROCESSING', " +
            "u.geoLocation.geoClaimedAt = CURRENT_TIMESTAMP "+
            "where u.id = :id AND u.geoLocation.geoStatus = 'PENDING'")
    int claimForGeoEnrichment(@Param("id") UUID id);

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.geoLocation.geoStatus = :nextStatus, " +
            "u.geoLocation.geoRetryCount = :attempts, " +
            "u.geoLocation.geoLastError = :error " +
            "WHERE u.id = :id")
    int updateGeoEnrichmentFailureStatus(@Param("id") UUID id,
                                         @Param("nextStatus") GeoStatus nextStatus,
                                         @Param("attempts") int attempts,
                                         @Param("error") String error);

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.geoLocation.latitude = :lat, u.geoLocation.longitude = :lon, " +
            "u.geoLocation.geoStatus = :status WHERE u.id = :id AND u.geoLocation.geoStatus = 'PROCESSING'")
    int applyGeoEnrichment(@Param("id") UUID id,
                           @Param("lat") Double lat,
                           @Param("lon") Double lon,
                           @Param("status") GeoStatus status);

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.geoLocation.latitude = :lat, u.geoLocation.longitude = :lon, " +
            "u.geoLocation.geoUpdatedAt = :geoUpdatedAt, u.geoLocation.geoReconfirmRequestedAt = NULL " +
            "WHERE u.id = :id")
    int applyLocationUpdate(@Param("id") UUID id,
                            @Param("lat") Double lat,
                            @Param("lon") Double lon,
                            @Param("geoUpdatedAt") Instant geoUpdatedAt);

    /**
     * Donors eligible for a location-reconfirmation email: still available, but their
     * coordinates haven't been confirmed since {@code stalenessCutoff}, and they
     * haven't been asked since {@code reconfirmCooldown} (no spam).
     */
    @Query("""
           SELECT u FROM User u
           WHERE u.isActive = true
             AND u.isAvailable = true
             AND u.isVerified = true
             AND (u.geoLocation.geoUpdatedAt IS NULL OR u.geoLocation.geoUpdatedAt < :stalenessCutoff)
             AND (u.geoLocation.geoReconfirmRequestedAt IS NULL OR u.geoLocation.geoReconfirmRequestedAt < :reconfirmCooldown)
           ORDER BY u.geoLocation.geoUpdatedAt ASC NULLS FIRST
           """)
    List<User> findDonorsWithStaleLocation(@Param("stalenessCutoff") Instant stalenessCutoff,
                                           @Param("reconfirmCooldown") Instant reconfirmCooldown,
                                           Pageable pageable);

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.geoLocation.geoReconfirmRequestedAt = :requestedAt WHERE u.id = :id")
    int markReconfirmationRequested(@Param("id") UUID id, @Param("requestedAt") Instant requestedAt);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findUserWithRolesAndPermissionsById(UUID usedId);

    /**
     * Records an approved donation: count++, freshness stamp, and (when rated)
     * the running-average rating. JPQL evaluates SET expressions against the
     * pre-update row, so the average math below is exact and race-safe.
     */
    @Transactional
    @Modifying
    @Query("""
           UPDATE User u
              SET u.totalDonations = u.totalDonations + 1,
                  u.lastDonationDate = :donatedAt,
                  u.ratingCount = u.ratingCount + 1,
                  u.rating = (u.rating * u.ratingCount + :rating) / (u.ratingCount + 1)
            WHERE u.id = :donorId
           """)
    int recordDonationRated(@Param("donorId") UUID donorId,
                            @Param("donatedAt") LocalDateTime donatedAt,
                            @Param("rating") int rating);

    /** Same, when the requester approved without leaving a rating. */
    @Transactional
    @Modifying
    @Query("""
           UPDATE User u
              SET u.totalDonations = u.totalDonations + 1,
                  u.lastDonationDate = :donatedAt
            WHERE u.id = :donorId
           """)
    int recordDonation(@Param("donorId") UUID donorId, @Param("donatedAt") LocalDateTime donatedAt);

    /** Grants the verified-donor badge; returns 1 only when it changed. */
    @Transactional
    @Modifying
    @Query("""
           UPDATE User u SET u.isVerified = true
            WHERE u.id = :userId AND u.isVerified = false
           """)
    int markVerifiedIfAbsent(@Param("userId") UUID userId);

    @Transactional
    @Modifying
    @Query("""
    UPDATE User u
       SET u.failedOtpAttempts = u.failedOtpAttempts + 1,
           u.lockedUntil =
               CASE
                   WHEN u.failedOtpAttempts + 1 >= :maxAttempts
                   THEN :lockedUntil
                   ELSE u.lockedUntil
               END
     WHERE u.id = :userId
     """)
    int registerFailedOtpAttempt(
            UUID userId,
            int maxAttempts,
            Instant lockedUntil
    );


}
