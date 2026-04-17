package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.model.User;
import com.bd.blooddonorfinder.payload.request.DonorSearchRequest;
import com.bd.blooddonorfinder.payload.request.SendMailRequest;
import com.bd.blooddonorfinder.utils.MailUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class NotificationManagerImpl implements NotificationManager {
    private final MailService mailService;

    public NotificationManagerImpl(MailService mailService) {
        this.mailService = mailService;
    }

    @Override
    public void notifyByMail(List<User> donors, DonorSearchRequest searchRequest) {
        for (User donor : donors) {
            if (MailUtils.isValidEmail(donor.getEmail())) {
                String donorName = donor.getName();
                SendMailRequest mailRequest = new SendMailRequest(
                        donor.getEmail(),
                        searchRequest.getReceiverEmail(), "Looking for blood", MailUtils.buildHtmlBody(donorName,
                                searchRequest.getGeoLocation().toString(),
                                searchRequest.getReceiverPhone(),
                                "DREAM"));
                mailRequest.setHtmlContent(true);
                mailService.sendMail(mailRequest);
            } else {
                log.error("Invalid Email");
            }
        }
    }
}