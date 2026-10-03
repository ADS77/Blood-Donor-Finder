package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.exception.*;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.OtpCode;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.OtpPurpose;
import com.bd.blooddonorfinder.repository.OtpCodeRepository;
import com.bd.blooddonorfinder.repository.UserRepository;
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
    private static final int OTP_MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 15L;
    private static final String PREFIX_OTP_RATE_LIMIT = "otp:rl:";
    private static final String PREFIX_OTP_STATE = "otp:";

    private final OtpCodeRepository otpCodeRepository;
    private final RedisTemplate<String, Object>redisTemplate;
    private final NotificationManager notificationManager;
    private final PiiService piiService;
    private final UserRepository userRepository;

    private final SecureRandom secureRandom = new SecureRandom();


    public OtpService(OtpCodeRepository otpCodeRepository,
                      RedisTemplate<String, Object> redisTemplate,
                      NotificationManager notificationManager,
                      PiiService piiService,
                      UserService userService,
                      UserRepository userRepository) {
        this.otpCodeRepository = otpCodeRepository;
        this.redisTemplate = redisTemplate;
        this.notificationManager = notificationManager;
        this.piiService = piiService;
        this.userRepository = userRepository;
    }

    @Transactional
    public int generateAndSend(User user, OtpPurpose purpose){
        String phone = piiService.decryptPhone(user);
        String email = piiService.decryptEmail(user);
        enforceRateLimit(phone);
        enforceRateLimit(email);
        otpCodeRepository.invalidatePreviousOtps(user, purpose, Instant.now());
        String plainOtp = generateOtp();
        String otpHash = HashUtils.sha256Hex(plainOtp);
        Instant expiresAt = Instant.now().plusSeconds(OTP_TTL_SECONDS);
        otpCodeRepository.save(OtpCode.of(user,otpHash,purpose,expiresAt));
        String otpStateKey = PREFIX_OTP_STATE + user.getId() + ":"+purpose.name();
        redisTemplate.opsForValue().set(otpStateKey, 1, OTP_TTL_SECONDS, TimeUnit.SECONDS);
        if(MailUtils.isValidEmail(email)){
            try {
                if( !notificationManager.notifyByMail(user, email, plainOtp,purpose.name())){
                    log.error("Failed to send otp by email, userID: {}", user.getId());
                    return 0;
                }
            } catch (Exception e) {
                //TO-DO:
                // Should not throw exception if mail delivery fails, should retry or use different otp channel
                // as of now, email is our only otp channel, can't but throw exception, will fix it in future
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
    public void verifyOtp(User user, String rawOtp, OtpPurpose purpose){
        OtpCode otpCode = otpCodeRepository
                .findActiveOtp(user,purpose,Instant.now())
                .orElseThrow(()-> new OtpExpiredException(ErrorCode.OTP_EXPIRED,"No active OTP found, request a new one"));
       /* if (otpCode.isUsed()) {
            throw new OtpAlreadyUsedException(ErrorCode.OTP_ALREADY_USED, "OTP has already been used");
        }*/
        log.debug("Found active otp code for {}, usedId: {}", purpose, user.getId());
        String submittedHash = HashUtils.sha256Hex(rawOtp);
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

    private void handleFailedAttempt(User user) {
        Instant lockedUntil = Instant.now().plus(Duration.ofMinutes(LOCKOUT_MINUTES));
        userRepository.registerFailedOtpAttempt(user.getId(), OTP_MAX_FAILED_ATTEMPTS, lockedUntil);
        log.warn("Otp verification failed, failedOtpAttempts : {}", user.getFailedOtpAttempts());
        if(user.getFailedOtpAttempts() >= OTP_MAX_FAILED_ATTEMPTS){
            user.lockUntil(lockedUntil);
            log.warn("User {} locked out after {} failed OTP attempts",
                    user.getId(), OTP_MAX_FAILED_ATTEMPTS);
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
