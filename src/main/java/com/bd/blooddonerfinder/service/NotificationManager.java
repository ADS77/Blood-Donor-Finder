package com.bd.blooddonerfinder.service;

import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.payload.request.DonorSearchRequest;

import java.util.List;

public interface NotificationManager {
    public void notifyByMail(List<User> donors, DonorSearchRequest searchRequest);
}
