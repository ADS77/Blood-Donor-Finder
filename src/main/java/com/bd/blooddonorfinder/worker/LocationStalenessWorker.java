package com.bd.blooddonorfinder.worker;

import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.NotificationType;
import com.bd.blooddonorfinder.repository.UserRepository;
import com.bd.blooddonorfinder.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Location-freshness reconciliation (read side).
 *
 * <p>Donors whose coordinates haven't been confirmed within the freshness window
 * are excluded from nearby searches by the staleness filter — this worker asks
 * them to reconfirm by creating a LOCATION_RECONFIRMATION notification (in-app
 * badge + daily digest email), so genuinely active donors rejoin the pool.
 * No direct email — the notification center owns the email policy.
 *
 * <p>Runs nightly under a ShedLock so only one instance sweeps at a time.
 * Each donor is marked <em>before</em> notification (at-most-once) and a
 * reconfirm-cooldown guarantees a donor is never asked twice within the
 * cooldown window even if the sweep overlaps or crashes.
 */
@Component
@Slf4j
public class LocationStalenessWorker {

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Value("${app.search.max-location-age-days:90}")
    private long maxLocationAgeDays;

    @Value("${geo.staleness.batch-size:50}")
    private int batchSize;

    @Value("${geo.staleness.max-per-run:500}")
    private int maxPerRun;

    @Value("${geo.staleness.reconfirm-cooldown-days:7}")
    private long reconfirmCooldownDays;

    @Value("${app.location-reconfirm.url:https://sondhan.app/location/confirm}")
    private String locationReconfirmUrl;

    public LocationStalenessWorker(UserRepository userRepository,
                                   NotificationService notificationService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${geo.staleness.cron:0 0 3 * * *}")
    @SchedulerLock(name = "locationStalenessWorker", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void reconfirmStaleDonorLocations() {
        LockAssert.assertLocked();

        Instant stalenessCutoff = Instant.now().minus(maxLocationAgeDays, ChronoUnit.DAYS);
        Instant reconfirmCooldown = Instant.now().minus(reconfirmCooldownDays, ChronoUnit.DAYS);
        log.info("Location staleness sweep starting: freshnessWindow={}d, cutoff={}",
                maxLocationAgeDays, stalenessCutoff);

        int notified = 0;
        List<User> batch;
        // rows are marked as-asked during the loop, so re-querying page 0 always progresses
        do {
            batch = userRepository.findDonorsWithStaleLocation(stalenessCutoff, reconfirmCooldown,
                    PageRequest.of(0, batchSize));
            for (User user : batch) {
                if (notified >= maxPerRun) {
                    log.info("Reconfirmation cap of {} reached — continuing next night", maxPerRun);
                    return;
                }
                try {
                    // mark first (at-most-once), then notify best-effort
                    userRepository.markReconfirmationRequested(user.getId(), Instant.now());
                    notificationService.notify(
                            user.getId(),
                            NotificationType.LOCATION_RECONFIRMATION,
                            user.getId(),
                            Map.of(
                                    "action", "CONFIRM_LOCATION",
                                    "confirmUrl", locationReconfirmUrl,
                                    "staleDays", maxLocationAgeDays
                            ));
                    notified++;
                } catch (Exception e) {
                    log.error("Failed to create location reconfirmation for userId={}: {}",
                            user.getId(), e.getMessage(), e);
                }
            }
        } while (batch.size() == batchSize);

        log.info("Location staleness sweep finished: {} reconfirmation notification(s) created", notified);
    }
}
