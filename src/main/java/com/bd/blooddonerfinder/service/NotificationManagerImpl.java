package com.bd.blooddonerfinder.service;

import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.payload.request.DonorSearchRequest;
import com.bd.blooddonerfinder.payload.request.SendMailRequest;
import com.bd.blooddonerfinder.util.MailUtils;
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
                        searchRequest.getReceiverEmail(),
                        "Looking for blood",
                        MailUtils.buildHtmlBody(
                                donorName,
                                searchRequest.getLocation().toString(),
                                searchRequest.getReceiverPhone(),
                                "DREAM"
                        ));
                mailRequest.setHtmlContent(true);
                mailService.sendMail(mailRequest);
            } else {
                log.error("Invalid Email");
            }
        }
    }
}