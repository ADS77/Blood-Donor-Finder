package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.kafka.outbox.EmailOutboxPublisher;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.EmailNotificationType;
import com.bd.blooddonorfinder.payload.request.DonorSearchRequest;
import com.bd.blooddonorfinder.payload.request.SendMailRequest;
import com.bd.blooddonorfinder.service.auth.PiiService;
import com.bd.blooddonorfinder.utils.MailUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Turns notification intents into durable outbox records.
 *
 * <p>This class no longer talks to SMTP. Enqueueing happens in the caller's
 * transaction, so an OTP email is only queued if the OTP row itself commits.
 * Actual transport is owned by {@code EmailNotificationConsumer}.</p>
 */
@Service
@Slf4j
public class NotificationManagerImpl implements NotificationManager {

    private final EmailOutboxPublisher emailOutboxPublisher;
    private final PiiService piiService;

    @Value("${app.org.name}")
    private String orgName;
    @Value("${app.email.from}")
    private String orgEmail;

    public NotificationManagerImpl(EmailOutboxPublisher emailOutboxPublisher,
                                   PiiService piiService) {
        this.emailOutboxPublisher = emailOutboxPublisher;
        this.piiService = piiService;
    }

    @Override
    @Transactional
    public boolean notifyByMail(User user, String email, String otp, String purpose) {
        String body = MailUtils.buildHtmlBodyForOtpMsg(user.getFirstName(), orgName, otp);
        String subject = "Sondhan verification code for " + purpose;
        SendMailRequest mailRequest = SendMailRequest.of(
                List.of(email),
                orgEmail,
                subject,
                body);

        String eventId = emailOutboxPublisher.enqueue(
                mailRequest, EmailNotificationType.OTP, user.getId());
        log.debug("Queued OTP email for userId={}, purpose={}, eventId={}",
                user.getId(), purpose, eventId);
        // true means the notification is durably queued, not that SMTP has
        // already accepted it.
        return true;
    }

    @Override
    @Transactional
    public void notifyByMail(List<User> eligibleDonors, DonorSearchRequest donorSearchRequest) {
        if (eligibleDonors == null || eligibleDonors.isEmpty()) {
            return;
        }

        String location = donorSearchRequest.getGeoLocation() != null
                ? donorSearchRequest.getGeoLocation().toString()
                : "our center";

        for (User donor : eligibleDonors) {
            String email;
            try {
                email = piiService.decryptEmail(donor);
            } catch (IllegalStateException e) {
                // Detached users from the Kafka donor-list message do not carry
                // PII tokens. The direct controller path does, so skip only the
                // donors whose email cannot be resolved.
                log.warn("Skipping donor notification for userId={}: {}",
                        donor.getId(), e.getMessage());
                continue;
            }

            if (!MailUtils.isValidEmail(email)) {
                log.warn("Skipping donor notification for userId={}: invalid email", donor.getId());
                continue;
            }

            String body = MailUtils.buildHtmlBodyForPingMsg(
                    donor.getFirstName(),
                    location,
                    donorSearchRequest.getReceiverPhone(),
                    orgName);
            SendMailRequest mailRequest = SendMailRequest.of(
                    List.of(email),
                    orgEmail,
                    "Looking for blood",
                    body);

            String eventId = emailOutboxPublisher.enqueue(
                    mailRequest, EmailNotificationType.DONOR_ALERT, donor.getId());
            log.debug("Queued donor alert for userId={}, eventId={}", donor.getId(), eventId);
        }
    }
}
