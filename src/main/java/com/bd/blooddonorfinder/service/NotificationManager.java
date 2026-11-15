package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.payload.request.DonorSearchRequest;

import java.util.List;

public interface NotificationManager {
    public boolean notifyByMail(User user, String email, String otp, String purpose);

    void notifyByMail(List<User> eligibleDonors, DonorSearchRequest donorSearchRequest);

    /**
     * Daily digest — the only product-event email. One summary email per user
     * per day listing unread notifications (chat messages, acceptances,
     * location-reconfirmation asks).
     *
     * @param items pre-rendered lines, e.g. "Donor B: On my way…"
     */
    boolean notifyDigestEmail(User recipient, List<String> items);
}
