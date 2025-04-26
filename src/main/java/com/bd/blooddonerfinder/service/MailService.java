package com.bd.blooddonerfinder.service;

public interface MailService {

    public void sendMail(String to, String subject, String body);
}
