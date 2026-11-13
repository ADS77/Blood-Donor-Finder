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
 * Delivers an OTP through any registered {@link OtpChannel}.
 *
 * <p>This service is intentionally channel-agnostic: it knows only that a
 * channel may be able to resolve a recipient and send a message. Spring
 * injects the channels in {@code @Order} order, so the preferred channel is
 * tried first and later channels act as fallbacks.</p>
 *
 * <p>Delivery failures use a stable error contract:
 * {@link ErrorCode#OTP_DELIVERY_UNAVAILABLE} when no channel can reach the
 * user, and {@link ErrorCode#OTP_SEND_FAILED} when every reachable channel
 * rejects the message.</p>
 */
@Service
public class OtpDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(OtpDeliveryService.class);

    private final List<OtpChannel> channels;

    public OtpDeliveryService(List<OtpChannel> channels) {
        this.channels = List.copyOf(channels);
    }

    /**
     * Delivers the OTP, trying channels in order until one accepts it.
     *
     * @throws AuthException with {@link ErrorCode#OTP_DELIVERY_UNAVAILABLE}
     *         when no channel can resolve a recipient for the user, or
     *         {@link ErrorCode#OTP_SEND_FAILED} when all reachable channels
     *         fail to send.
     */
    public void deliver(User user, String otp, OtpPurpose purpose) {
        boolean reachable = false;

        for (OtpChannel channel : channels) {
            Optional<String> recipient = channel.resolveRecipient(user);
            if (recipient.isEmpty()) {
                continue;
            }
            reachable = true;
            OtpMessage message = new OtpMessage(user, recipient.get(), otp, purpose);

            try {
                if (channel.send(message)) {
                    log.debug("Delivered {} OTP to userId={} via {}",
                            purpose, user.getId(), channel.type());
                    return;
                }
                log.warn("Channel {} rejected {} OTP for userId={}",
                        channel.type(), purpose, user.getId());
            } catch (AuthException e) {
                if (e.getErrorCode() != ErrorCode.OTP_SEND_FAILED) {
                    throw e;
                }
                // A provider failure must not leak channel details to the
                // client; try the next channel before reporting a failure.
                log.warn("Channel {} failed to deliver {} OTP for userId={}",
                        channel.type(), purpose, user.getId(), e);
            }
        }

        if (!reachable) {
            throw new AuthException(ErrorCode.OTP_DELIVERY_UNAVAILABLE,
                    "No delivery channel is available for this user");
        }
        throw new AuthException(ErrorCode.OTP_SEND_FAILED,
                "Failed to send OTP. Please try again later.");
    }
}
