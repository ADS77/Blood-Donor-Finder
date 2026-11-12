package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.exception.*;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.OtpCode;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.OtpPurpose;
import com.bd.blooddonorfinder.repository.OtpCodeRepository;
import com.bd.blooddonorfinder.service.auth.PiiService;
import com.bd.blooddonorfinder.service.otp.OtpFailureTracker;
import com.bd.blooddonorfinder.utils.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private static final int OTP_TTL_SECONDS = 180;
    private static final int MAX_OTP_REQUESTS_PER_HOUR = 10;
    private static final int OTP_MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 15L;
    private static final String PREFIX_OTP_RATE_LIMIT = "otp:rl:";
    private static final String PREFIX_OTP_STATE = "otp:";

    /**
     * The freshly generated OTP and its lifetime. The plain code must only be
     * handed to an OTP delivery channel and must never be logged.
     */
    public record GeneratedOtp(String code, int ttlSeconds) {

        /**
         * Keeps the plain OTP out of logs if the result is ever interpolated.
         */
        @Override
        public String toString() {
            return "GeneratedOtp[ttlSeconds=" + ttlSeconds + "]";
        }
    }

    private final OtpCodeRepository otpCodeRepository;
    private final RedisTemplate<String, Object>redisTemplate;
    private final PiiService piiService;
    private final OtpFailureTracker otpFailureTracker;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${sondhan.otp.hash-key}")
    private String otpHashKey;


    public OtpService(OtpCodeRepository otpCodeRepository,
                      RedisTemplate<String, Object> redisTemplate,
                      PiiService piiService,
                      OtpFailureTracker otpFailureTracker) {
        this.otpCodeRepository = otpCodeRepository;
        this.redisTemplate = redisTemplate;
        this.piiService = piiService;
        this.otpFailureTracker = otpFailureTracker;
    }

    /**
     * Generates, hashes and persists a new OTP. Delivery is intentionally not
     * part of this responsibility; callers issue the OTP through
     * {@code OtpIssuanceService}.
     */
    @Transactional
    public GeneratedOtp generate(User user, OtpPurpose purpose){
        String phone = piiService.decryptPhone(user);
        String email = piiService.decryptEmail(user);
        enforceRateLimit(phone);
        enforceRateLimit(email);
        otpCodeRepository.invalidatePreviousOtps(user, purpose, Instant.now());
        String plainOtp = generateOtp();
        String otpHash = hashOtp(user, purpose, plainOtp);
        Instant expiresAt = Instant.now().plusSeconds(OTP_TTL_SECONDS);
        otpCodeRepository.save(OtpCode.of(user,otpHash,purpose,expiresAt));
        String redisOtpKey = PREFIX_OTP_STATE + user.getId() + ":"+purpose.name();
        redisTemplate.opsForValue().set(redisOtpKey, 1, OTP_TTL_SECONDS, TimeUnit.SECONDS);
        return new GeneratedOtp(plainOtp, OTP_TTL_SECONDS);
    }

    @Transactional
    public void verifyOtp(User user, String rawOtp, OtpPurpose purpose){
        OtpCode otpCode = otpCodeRepository
                .findActiveOtp(user,purpose,Instant.now())
                .orElseThrow(() -> noActiveOtpException(user, purpose));
        log.debug("Found active otp code for {}, usedId: {}", purpose, user.getId());
        String submittedHash = hashOtp(user, purpose, rawOtp);
        if(!submittedHash.equals(otpCode.getCodeHash())){
            handleFailedAttempt(user);
            throw new InvalidOtpException(ErrorCode.INVALID_OTP, "OTP is incorrect");
        }
        log.debug("Otp verification successful for {}, usedId: {}", purpose, user.getId());
        int update = otpCodeRepository.markAsUsed(otpCode.getId(),Instant.now());
        if(update != 1){
            throw new OtpAlreadyUsedException(ErrorCode.OTP_ALREADY_USED, "OTP has already been used");
        }
    }

    /**
     * Reports an already-used OTP as such, while a missing or expired OTP keeps
     * the existing "request a new one" contract.
     */
    private AuthException noActiveOtpException(User user, OtpPurpose purpose) {
        Optional<OtpCode> latestOtp =
                otpCodeRepository.findFirstByUserAndPurposeOrderByCreatedAtDesc(user, purpose);
        if (latestOtp.isPresent() && latestOtp.get().isUsed()) {
            return new OtpAlreadyUsedException(
                    ErrorCode.OTP_ALREADY_USED, "OTP has already been used");
        }
        return new OtpExpiredException(
                ErrorCode.OTP_EXPIRED, "No active OTP found, request a new one");
    }

    private void handleFailedAttempt(User user) {
        Instant lockedUntil = Instant.now().plus(Duration.ofMinutes(LOCKOUT_MINUTES));
        otpFailureTracker.recordFailure(user.getId(), OTP_MAX_FAILED_ATTEMPTS, lockedUntil);
        log.warn("Otp verification failed for userId: {}", user.getId());
    }

    private String generateOtp() {
        int code = 100_000+secureRandom.nextInt(900_000);
        return String.valueOf(code);
    }

    private String hashOtp(User user, OtpPurpose purpose, String otp) {
        String binding = user.getId() + ":" + purpose.name() + ":" + otp;
        return HashUtils.hmacSha256Hex(otpHashKey, binding);
    }

    private void enforceRateLimit(String rlKey) {
        String key = PREFIX_OTP_RATE_LIMIT+ HashUtils.sha256Hex(rlKey);
        Long count = redisTemplate.opsForValue().increment(key);
        if(count != null && count == 1L){
            redisTemplate.expire(key, 1, TimeUnit.HOURS);
        }
        if(count != null && count > MAX_OTP_REQUESTS_PER_HOUR){
            throw  new AuthException(ErrorCode.RATE_LIMITED, "Too many OTP request, try again later!");
        }
    }
}
