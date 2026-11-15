package com.bd.blooddonorfinder.worker;

import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.NotificationType;
import com.bd.blooddonorfinder.model.notification.Notification;
import com.bd.blooddonorfinder.payload.response.NotificationDto;
import com.bd.blooddonorfinder.repository.NotificationRepository;
import com.bd.blooddonorfinder.repository.UserRepository;
import com.bd.blooddonorfinder.service.NotificationManager;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The ONLY component that emails product events. Daily digest, Facebook-style
 * re-engagement policy:
 *
 * <ul>
 *   <li>a user is emailed at most once per {@code digest-cooldown-hours}, and</li>
 *   <li>only when they have unread notifications older than {@code digest-min-age-hours}.</li>
 *   <li>each notification row is marked when included, so it never emails twice.</li>
 * </ul>
 *
 * Users active in the app (clearing their bell) never get email. Inactive users
 * get one bundled email per day instead of a flood.
 */
@Component
@Slf4j
public class NotificationDigestWorker {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationManager notificationManager;

    @Value("${app.notification.digest.min-age-hours:24}")
    private long minAgeHours;

    @Value("${app.notification.digest.cooldown-hours:24}")
    private long cooldownHours;

    @Value("${app.notification.digest.batch-size:100}")
    private int batchSize;

    @Value("${app.notification.digest.max-per-run:2000}")
    private int maxPerRun;

    @Value("${app.notification.digest.items-per-email:10}")
    private int itemsPerEmail;

    public NotificationDigestWorker(NotificationRepository notificationRepository,
                                    UserRepository userRepository,
                                    NotificationManager notificationManager) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.notificationManager = notificationManager;
    }

    @Scheduled(cron = "${app.notification.digest.cron:0 0 9 * * *}")
    @SchedulerLock(name = "notificationDigestWorker", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void sendDigests() {
        LockAssert.assertLocked();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime minAgeCutoff = now.minusHours(minAgeHours);
        LocalDateTime cooldownCutoff = now.minusHours(cooldownHours);

        log.info("Notification digest sweep starting (minAge={}h, cooldown={}h)", minAgeHours, cooldownHours);
        int emailed = 0;
        UUID afterId = null;
        while (emailed < maxPerRun) {
            List<UUID> candidates = notificationRepository.findDigestCandidateUserIds(
                    minAgeCutoff, cooldownCutoff, afterId, batchSize);
            if (candidates.isEmpty()) {
                break;
            }
            afterId = candidates.get(candidates.size() - 1);
            for (UUID userId : candidates) {
                if (emailed >= maxPerRun) {
                    log.info("Digest cap of {} reached — continuing tomorrow", maxPerRun);
                    break;
                }
                try {
                    if (emailDigestFor(userId, now)) {
                        emailed++;
                    }
                } catch (Exception e) {
                    log.error("Failed to send digest for userId={}: {}", userId, e.getMessage(), e);
                }
            }
            // A short page means there are no further candidates this run.
            if (candidates.size() < batchSize) {
                break;
            }
        }

        log.info("Notification digest sweep finished: {} digest email(s) sent", emailed);
    }

    /** Builds, sends and marks the digest for one user. Returns false on mail failure. */
    private boolean emailDigestFor(UUID userId, LocalDateTime now) {
        List<Notification> undigested =
                notificationRepository.findUndigestedUnread(userId, PageRequest.of(0, itemsPerEmail));
        if (undigested.isEmpty()) {
            return false;
        }
        User recipient = userRepository.findById(userId).orElse(null);
        if (recipient == null) {
            return false;
        }
        boolean sent = notificationManager.notifyDigestEmail(recipient, renderLines(undigested));
        if (sent) {
            notificationRepository.markDigested(undigested.stream().map(Notification::getId).toList(), now);
        }
        return sent;
    }

    private List<String> renderLines(List<Notification> notifications) {
        return notifications.stream()
                .map(this::renderLine)
                .toList();
    }

    private String renderLine(Notification notification) {
        Object senderName = notification.getPayload().get("senderName");
        Object preview = notification.getPayload().get("preview");
        return switch (notification.getType()) {
            case CHAT_MESSAGE -> (senderName != null ? senderName + ": " : "New message: ")
                    + (preview != null ? preview : "");
            case REQUEST_ACCEPTED -> "Good news — a donor accepted your blood request"
                    + (senderName != null ? " (" + senderName + ")" : "") + ". A chat is open.";
            case LOCATION_RECONFIRMATION -> "Please confirm your current location so patients nearby can find you.";
            case DONATION_COMPLETED -> "Your donor marked the blood donation as complete — please confirm it in the app.";
            case DONATION_APPROVED -> "Donation confirmed! Your donation count and rating have been updated.";
            case DONATION_DISPUTED -> "The requester reported an issue with the donation completion — please resolve it in the app.";
            case URGENT_REQUEST_ALERT -> "URGENT: " + (notification.getPayload().get("bloodGroup") != null
                    ? notification.getPayload().get("bloodGroup") + " blood needed" : "blood needed")
                    + " near you — open the app to respond.";
            case DOCUMENT_VERIFIED -> "Your medical document was verified — you are now a confirmed donor.";
        };
    }
}
