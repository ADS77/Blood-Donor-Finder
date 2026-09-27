package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.payload.request.DonorSearchRequest;

import java.util.List;

public interface NotificationManager {
    public boolean notifyByMail(User user, String email, String otp, String purpose);

    void notifyByMail(List<User> eligibleDonors, DonorSearchRequest donorSearchRequest);
}
