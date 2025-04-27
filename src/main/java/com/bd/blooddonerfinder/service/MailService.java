package com.bd.blooddonerfinder.service;

import com.bd.blooddonerfinder.payload.request.SendMailRequest;

public interface MailService {

    public void sendMail(SendMailRequest mailRequest);
}
