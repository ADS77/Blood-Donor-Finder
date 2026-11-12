package com.bd.blooddonorfinder.service.otp;

import com.bd.blooddonorfinder.exception.AuthException;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.OtpPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Selects the first channel that can reach the user and delivers the OTP.
 * Channels are ordered by Spring, so email is preferred while an SMS channel
 * can be added later without touching this class.
 */
@Service
public class OtpDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(OtpDeliveryService.class);

    private final List<OtpChannel> channels;

    public OtpDeliveryService(List<OtpChannel> channels) {
        this.channels = List.copyOf(channels);
    }

    public boolean deliver(User user, String otp, OtpPurpose purpose) {
        for (OtpChannel channel : channels) {
            Optional<String> recipient = channel.resolveRecipient(user);
            if (recipient.isEmpty()) {
                continue;
            }
            log.debug("Delivering {} OTP to userId={} via {}", purpose, user.getId(), channel.type());
            return channel.send(new OtpMessage(user, recipient.get(), otp, purpose));
        }
        // Email is the only channel today. Keep the existing error contract
        // until a channel-agnostic code is introduced alongside SMS.
        throw new AuthException(ErrorCode.INVALID_EMAIL, "Email is not valid");
    }
}
