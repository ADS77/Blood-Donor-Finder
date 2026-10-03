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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByPhoneToken(PiiToken phoneToken);
    Optional<User>findByEmailToken(PiiToken emailToken);
    Optional<User>findByFirstName(String firstName);
    boolean existsByEmailToken(PiiToken emailToken);
    boolean existsByPhoneToken(PiiToken phoneToken);

    List<User> findNearByAndBloodGroupAndGeoLocationCity(BloodGroup bloodGroup, String city);
    List<User> findByGeoLocation_GeoStatusOrderByCreatedAtAsc(GeoStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u set u.geoLocation.geoStatus = 'PROCESSING', " +
            "u.geoLocation.geoClaimedAt = CURRENT_TIMESTAMP "+
            "where u.id = :id AND u.geoLocation.geoStatus = 'PENDING'")
    int claimForGeoEnrichment(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE User u SET u.geoLocation.geoStatus = :nextStatus, " +
            "u.geoLocation.geoRetryCount = :attempts, " +
            "u.geoLocation.geoLastError = :error " +
            "WHERE u.id = :id")
    int updateGeoEnrichmentFailureStatus(@Param("id") UUID id,
                                         @Param("nextStatus") GeoStatus nextStatus,
                                         @Param("attempts") int attempts,
                                         @Param("error") String error);

    @Modifying
    @Query("UPDATE User u SET u.geoLocation.latitude = :lat, u.geoLocation.longitude = :lon, " +
            "u.geoLocation.geoStatus = :status WHERE u.id = :id AND u.geoLocation.geoStatus = 'PROCESSING'")
    int applyGeoEnrichment(@Param("id") UUID id,
                           @Param("lat") Double lat,
                           @Param("lon") Double lon,
                           @Param("status") GeoStatus status);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findUserWithRolesAndPermissionsById(UUID usedId);

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
