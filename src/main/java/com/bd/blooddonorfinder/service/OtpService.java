package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.exception.AuthException;
import com.bd.blooddonorfinder.exception.ErrorCode;
import com.bd.blooddonorfinder.model.OtpCode;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.OtpPurpose;
import com.bd.blooddonorfinder.repository.OtpRepository;
import com.bd.blooddonorfinder.service.auth.PiiService;
import com.bd.blooddonorfinder.utils.HashUtils;
import com.bd.blooddonorfinder.utils.MailUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private static final int OTP_LENGTH = 6;
    private static final int OTP_TTL_SECONDS = 180;
    private static final int MAX_OTP_REQUESTS_PER_HOUR = 10;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 15L;
    private static final String PREFIX_OTP_RATE_LIMIT = "otp:rl:";
    private static final String PREFIX_OTP_STATE = "otp:";

    private final OtpRepository otpRepository;
    private final RedisTemplate<String, Object>redisTemplate;
    private final NotificationManager notificationManager;
    private final PiiService piiService;

    private final SecureRandom secureRandom = new SecureRandom();


    public OtpService(OtpRepository otpRepository,
                      RedisTemplate<String, Object> redisTemplate,
                      NotificationManager notificationManager,
                      PiiService piiService) {
        this.otpRepository = otpRepository;
        this.redisTemplate = redisTemplate;
        this.notificationManager = notificationManager;
        this.piiService = piiService;
    }

    @Transactional
    public int generateAndSend(User user, OtpPurpose purpose){
        String phone = piiService.decryptPhone(user);
        String email = piiService.decryptEmail(user);
        enforceRateLimit(phone);
        enforceRateLimit(email);
        otpRepository.invalidatePreviousOtps(user, purpose, Instant.now());
        String plainOtp = generateOtp();
        String otpHash = HashUtils.sha256Hex(plainOtp);
        Instant expiresAt = Instant.now().plusSeconds(OTP_TTL_SECONDS);
        otpRepository.save(OtpCode.of(user,otpHash,purpose,expiresAt));
        String otpStateKey = PREFIX_OTP_STATE + user.getId() + ":"+purpose.name();
        redisTemplate.opsForValue().set(otpStateKey, 1, OTP_TTL_SECONDS, TimeUnit.SECONDS);
        if(MailUtils.isValidEmail(email)){
            try {
                if( !notificationManager.notifyByMail(user, email, plainOtp,purpose.name())){
                    log.error("Failed to send otp by email, userID: {}", user.getId());
                    return 0;
                }
            } catch (Exception e) {
                throw new AuthException(ErrorCode.OTP_SEND_FAILED, "Failed to send otp via email, " + e.getMessage());
            }
        }
        else {
            log.error("Invalid Email : {}", email);
            throw new AuthException(ErrorCode.INVALID_EMAIL, "Email is not valid");
        }

        //TO-DO : need to integrate other otp channels
        log.info("OTP sent to user {},  purpose {}", user.getId(), purpose);
        return OTP_TTL_SECONDS;
    }

    @Transactional
    public void verify(User user, String rawOtp, OtpPurpose purpose){
        if(user.isLocked()){
            throw new AuthException(ErrorCode.ACCOUNT_LOCKED,
                    "Account is locked. Try again after " + user.getLockedUntil());
        }

        OtpCode otpCode = otpRepository
                .findActiveOtp(user,purpose,Instant.now())
                .orElseThrow(()-> new AuthException(ErrorCode.OTP_EXPIRED,"No active OTP found, request a new one"));
        if (otpCode.isExpired()) {
            throw new AuthException(ErrorCode.OTP_EXPIRED, "OTP has expired");
        }

        String submittedHash = HashUtils.sha256Hex(rawOtp);
        if(!submittedHash.equals(otpCode.getCodeHash())){
            handleFailedAttempt(user);
            throw new AuthException(ErrorCode.INVALID_OTP, "OTP is incorrect or expired");
        }
    }

    private void handleFailedAttempt(User user) {
        user.incrementFailedOtpAttempts();
        if(user.getFailedOtpAttempts() >= MAX_FAILED_ATTEMPTS){
            user.lockUntil(Instant.now().plus(Duration.ofMinutes(LOCKOUT_MINUTES)));
            log.warn("User {} locked out after {} failed OTP attempts",
                    user.getId(), MAX_FAILED_ATTEMPTS);
        }
    }

    private String generateOtp() {
        int code = 100_000+secureRandom.nextInt(900_000);
        return String.valueOf(code);
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
