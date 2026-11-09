package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.payload.request.DonorSearchRequest;
import com.bd.blooddonorfinder.payload.request.SendMailRequest;
import com.bd.blooddonorfinder.utils.MailUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class NotificationManagerImpl implements NotificationManager {
    private final MailService mailService;

    @Value("${app.org.name}")
    private String orgName;
    @Value("${app.email.from}")
    private String orgEmail;

    public NotificationManagerImpl(MailService mailService) {
        this.mailService = mailService;
    }

/*    @Override
    public void notifyByMail(List<User> donors, DonorSearchRequest searchRequest) {
        for (User donor : donors) {
            if (MailUtils.isValidEmail(donor.getEmail())) {
                String donorName = donor.getName();
                SendMailRequest mailRequest = new SendMailRequest(
                        donor.getEmail(),
                        searchRequest.getReceiverEmail(),
                        "Looking for blood",
                        MailUtils.buildHtmlBodyForPingMsg(donorName,
                                searchRequest.getGeoLocation().toString(),
                                searchRequest.getReceiverPhone(),
                                "DREAM")
                );
                mailRequest.setHtmlContent(true);
                mailService.sendMail(mailRequest);
            } else {
                log.error("Invalid Email");
            }
        }
    }*/

    @Override
    public boolean notifyByMail(User user, String email, String otp, String purpose) {
        // To-DO : Delegate email sending task by event
        String body = MailUtils.buildHtmlBodyForOtpMsg(user.getFirstName(),"Team Sondhan", otp);
        String subject = "Sondhan verification code for"+purpose;
        SendMailRequest mailRequest = SendMailRequest.of(
                Collections.singletonList(email),
                orgEmail,
                subject,
                body);
        return mailService.sendMail(mailRequest);
    }

    @Override
    public void notifyByMail(List<User> eligibleDonors, DonorSearchRequest donorSearchRequest) {
        // Temporarily added for backward compatibility
    }
}