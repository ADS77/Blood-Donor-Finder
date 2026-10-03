package com.bd.blooddonorfinder.repository;

import com.bd.blooddonorfinder.model.OtpCode;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {
    @Query(
            """
                    SELECT o FROM OtpCode o
                    WHERE o.user = :user
                      AND o.purpose = :purpose
                      AND o.usedAt IS NULL
                      AND o.expiresAt > :now
                    ORDER BY o.createdAt DESC
                    """)
    Optional<OtpCode> findActiveOtp(@Param("user") User user,
                                    @Param("purpose") OtpPurpose purpose,
                                    @Param("now") Instant now);

    @Modifying
    @Query(
            """
                    UPDATE OtpCode o SET o.usedAt = :now
                    WHERE o.user = :user
                      AND o.purpose = :purpose
                      AND o.usedAt IS NULL
                    """)
    void invalidatePreviousOtps(@Param("user") User user,
                                @Param("purpose") OtpPurpose purpose,
                                @Param("now") Instant now);

    @Modifying
    @Query("""
    UPDATE OtpCode o
       SET o.usedAt = :usedAt
     WHERE o.id = :otpId
       AND o.usedAt IS NULL
       """)
    int markAsUsed(
            @Param("otpId") UUID otpId,
            @Param("usedAt") Instant usedAt
    );

}
