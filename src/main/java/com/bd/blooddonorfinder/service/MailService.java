package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.payload.request.SendMailRequest;

public interface MailService {

    public void sendMail(SendMailRequest mailRequest);
}
