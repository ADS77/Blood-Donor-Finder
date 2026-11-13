package com.bd.blooddonorfinder.service.otp;

import com.bd.blooddonorfinder.exception.AuthException;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.service.NotificationManager;
import com.bd.blooddonorfinder.service.auth.PiiService;
import com.bd.blooddonorfinder.utils.MailUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(0)
public class EmailOtpChannel implements OtpChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailOtpChannel.class);

    private final PiiService piiService;
    private final NotificationManager notificationManager;

    public EmailOtpChannel(PiiService piiService, NotificationManager notificationManager) {
        this.piiService = piiService;
        this.notificationManager = notificationManager;
    }

    @Override
    public OtpChannelType type() {
        return OtpChannelType.EMAIL;
    }

    @Override
    public Optional<String> resolveRecipient(User user) {
        String email = piiService.decryptEmail(user);
        if (!MailUtils.isValidEmail(email)) {
            log.warn("User {} has no valid email address for OTP delivery", user.getId());
            return Optional.empty();
        }
        return Optional.of(email);
    }

    @Override
    public boolean send(OtpMessage message) {
        try {
            // true means the notification was durably enqueued in the
            // transactional outbox. The Kafka consumer owns the SMTP attempt.
            boolean queued = notificationManager.notifyByMail(
                    message.user(),
                    message.recipient(),
                    message.otp(),
                    message.purpose().name());
            if (!queued) {
                log.error("Failed to queue OTP email, userId: {}", message.user().getId());
            }
            return queued;
        } catch (Exception e) {
            // Log the cause server-side, but never surface mail-server
            // details (hosts, response codes) to the caller.
            log.error("Failed to send OTP email for userId={}", message.user().getId(), e);
            throw new AuthException(ErrorCode.OTP_SEND_FAILED,
                    "Failed to send OTP. Please try again later.");
        }
    }
}
