package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.model.User;
import com.bd.blooddonorfinder.payload.request.DonorSearchRequest;

import java.util.List;

public interface NotificationManager {
    public void notifyByMail(List<User> donors, DonorSearchRequest searchRequest);
}
