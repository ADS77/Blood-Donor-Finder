package com.bd.blooddonorfinder.service.otp;

import com.bd.blooddonorfinder.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists a failed OTP attempt in its own transaction.
 *
 * <p>A wrong OTP makes verification throw, which rolls back the caller's
 * transaction. Recording the attempt with {@link Propagation#REQUIRES_NEW}
 * ensures the per-OTP lockout counter survives that rollback.</p>
 */
@Service
public class OtpFailureTracker {

    private final UserRepository userRepository;

    public OtpFailureTracker(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recordFailure(UUID userId, int maxAttempts, Instant lockedUntil) {
        return userRepository.registerFailedOtpAttempt(userId, maxAttempts, lockedUntil);
    }
}
