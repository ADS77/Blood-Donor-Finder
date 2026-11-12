package com.bd.blooddonorfinder.service.otp;

import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.OtpPurpose;

/**
 * A resolved OTP delivery request. The recipient address has already been
 * decrypted and validated by the channel that produced it.
 */
public record OtpMessage(User user, String recipient, String otp, OtpPurpose purpose) {

    /**
     * Keeps the plain OTP out of logs if the message is ever interpolated.
     */
    @Override
    public String toString() {
        return "OtpMessage[user=" + user.getId() + ", purpose=" + purpose + "]";
    }
}
