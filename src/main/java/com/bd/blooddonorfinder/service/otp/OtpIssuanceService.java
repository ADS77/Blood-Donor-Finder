package com.bd.blooddonorfinder.service.otp;

import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.OtpPurpose;
import com.bd.blooddonorfinder.service.OtpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the "issue an OTP" use case: generation and persistence are
 * handled by {@link OtpService}, delivery by {@link OtpDeliveryService}.
 */
@Service
public class OtpIssuanceService {

    private static final Logger log = LoggerFactory.getLogger(OtpIssuanceService.class);

    private final OtpService otpService;
    private final OtpDeliveryService otpDeliveryService;

    public OtpIssuanceService(OtpService otpService, OtpDeliveryService otpDeliveryService) {
        this.otpService = otpService;
        this.otpDeliveryService = otpDeliveryService;
    }

    /**
     * Generates and delivers an OTP, returning its lifetime in seconds, or
     * {@code 0} when the delivery provider rejected the message.
     */
    @Transactional
    public int issue(User user, OtpPurpose purpose) {
        OtpService.GeneratedOtp generatedOtp = otpService.generate(user, purpose);
        boolean delivered = otpDeliveryService.deliver(user, generatedOtp.code(), purpose);
        if (!delivered) {
            return 0;
        }
        log.info("OTP sent to user {}, purpose {}", user.getId(), purpose);
        return generatedOtp.ttlSeconds();
    }
}
