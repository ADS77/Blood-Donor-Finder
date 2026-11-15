package com.bd.blooddonorfinder.worker;

import com.bd.blooddonorfinder.repository.BloodRequestRepository;
import com.bd.blooddonorfinder.service.DonationService;
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
import java.util.UUID;

/**
 * Trust-with-timeout: if a requester never reacts to a donor's completion
 * claim, the donation is auto-confirmed after {@code auto-approve-days} so
 * donors aren't held hostage by unresponsive requesters (no rating applied).
 * Idempotent — the conditional transition in the service guarantees each
 * request is processed at most once, even across overlapping sweeps.
 */
@Component
@Slf4j
public class DonationCompletionWorker {

    private final BloodRequestRepository bloodRequestRepository;
    private final DonationService donationService;

    @Value("${app.donation.auto-approve-days:3}")
    private long autoApproveDays;

    @Value("${app.donation.auto-approve.max-per-run:500}")
    private int maxPerRun;

    public DonationCompletionWorker(BloodRequestRepository bloodRequestRepository,
                                    DonationService donationService) {
        this.bloodRequestRepository = bloodRequestRepository;
        this.donationService = donationService;
    }

    @Scheduled(cron = "${app.donation.auto-approve.cron:0 0 * * * *}")
    @SchedulerLock(name = "donationCompletionWorker", lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")
    public void autoApproveStaleCompletions() {
        LockAssert.assertLocked();

        Instant cutoff = Instant.now().minus(autoApproveDays, ChronoUnit.DAYS);
        List<UUID> staleIds = bloodRequestRepository.findStaleCompletionIds(cutoff,
                PageRequest.of(0, maxPerRun));
        log.info("Donation auto-approval sweep: {} stale completion claim(s) older than {} day(s)",
                staleIds.size(), autoApproveDays);

        int approved = 0;
        for (UUID requestId : staleIds) {
            try {
                // each call is its own transaction — one failure never poisons the batch
                donationService.autoApprove(requestId);
                approved++;
            } catch (Exception e) {
                log.error("Auto-approval failed for request={}: {}", requestId, e.getMessage(), e);
            }
        }
        log.info("Donation auto-approval sweep finished: {} request(s) confirmed", approved);
    }
}
