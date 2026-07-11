package com.bd.blooddonorfinder.worker;

import com.bd.blooddonorfinder.model.User;
import com.bd.blooddonorfinder.model.enums.GeoStatus;
import com.bd.blooddonorfinder.repository.UserRepository;
import com.bd.blooddonorfinder.service.GeoLocationService;
import com.bd.blooddonorfinder.worker.service.GeoEnrichmentTransactionalService;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@Slf4j
public class GeoEnrichmentWorker {
    private final UserRepository userRepository;
    private final GeoEnrichmentTransactionalService geoEnrichmentTransactionalService;

    private static final int BATCH_SIZE = 20;
    private static final long THROTTLE_MS = 1100;

    public GeoEnrichmentWorker(UserRepository userRepository,GeoEnrichmentTransactionalService geoEnrichmentTransactionalService) {
        this.userRepository = userRepository;
        this.geoEnrichmentTransactionalService = geoEnrichmentTransactionalService;
    }

    @Scheduled(fixedDelayString = "${geo.enrichment.poll-interval:30m}")
    @SchedulerLock(name = "geoEnrichmentWorker", lockAtLeastFor = "PT10S", lockAtMostFor = "PT5M")
    public void processPendingGeoEnrichment(){
        log.info("Geo enrichment worker starts at: {}", Instant.now());
        LockAssert.assertLocked();

        List<User> batch = userRepository.findByGeoLocation_GeoStatusOrderByCreatedAtAsc(
                GeoStatus.PENDING, PageRequest.of(0, BATCH_SIZE));
        if(batch.isEmpty()){
            log.debug("No pending geo enrichment to process");
            return;
        }
        log.info("Processing {} pending geo enrichment", batch.size());
        for (User user : batch){
            try {
                geoEnrichmentTransactionalService.enrichSingleUser(user.getId());
            } catch (Exception e){
                log.error("Unexpected error enriching userId={}: {}", user.getId(), e.getMessage(), e);
            }
            sleepQuietly(THROTTLE_MS);
        }

    }

    private void sleepQuietly(long throttleMs) {
        try {
            Thread.sleep(throttleMs);
        } catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }

}
