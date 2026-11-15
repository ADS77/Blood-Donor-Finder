package com.bd.blooddonorfinder.repository;

import com.bd.blooddonorfinder.model.push.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PushTokenRepository extends JpaRepository<PushToken, UUID> {

    List<PushToken> findByUserId(UUID userId);

    Optional<PushToken> findByToken(String token);

    void deleteByUserIdAndToken(UUID userId, String token);

    void deleteByUserId(UUID userId);

    /** Removes every device token that FCM reported as invalid/unregistered. */
    @Transactional
    @Modifying
    @Query("DELETE FROM PushToken t WHERE t.token = :token")
    int deleteByToken(@Param("token") String token);
}
