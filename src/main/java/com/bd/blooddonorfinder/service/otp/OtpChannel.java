package com.bd.blooddonorfinder.service.otp;

import com.bd.blooddonorfinder.model.common.User;

import java.util.Optional;

/**
 * Transfers an OTP to a user through a specific medium.
 *
 * <p>Implementations are responsible only for resolving their own recipient
 * address and delivering the message. OTP generation, persistence and
 * verification stay in {@code OtpService}.</p>
 *
 * <p>To add SMS delivery, implement this interface, register the bean, and
 * give it an order after {@code EmailOtpChannel}. No change to the OTP
 * generation or verification code is required.</p>
 */
public interface OtpChannel {

    OtpChannelType type();

    /**
     * Resolves a usable recipient for the user, or empty when this channel
     * cannot reach the user. Implementations should return empty for missing
     * or invalid contact details so that the next channel can be tried;
     * infrastructure or data-integrity failures may still propagate.
     */
    Optional<String> resolveRecipient(User user);

    /**
     * Delivers the OTP. Returns {@code false} when the provider rejects the
     * message without throwing, or throws
     * {@link com.bd.blooddonorfinder.exception.AuthException} with
     * {@code ErrorCode.OTP_SEND_FAILED} when the provider fails. The delivery
     * service treats both outcomes as a channel failure and tries the next
     * reachable channel. Any other exception is treated as a programming or
     * infrastructure error and propagates to the caller.
     */
    boolean send(OtpMessage message);
}
