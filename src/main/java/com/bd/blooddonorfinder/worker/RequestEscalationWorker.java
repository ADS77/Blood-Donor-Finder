package com.bd.blooddonorfinder.worker;

import com.bd.blooddonorfinder.model.BloodRequest;
import com.bd.blooddonorfinder.model.enums.RequestUrgency;
import com.bd.blooddonorfinder.model.es.documents.DonorSearchDocument;
import com.bd.blooddonorfinder.model.enums.NotificationType;
import com.bd.blooddonorfinder.payload.response.NearbyDonorSearchResponse;
import com.bd.blooddonorfinder.repository.BloodRequestRepository;
import com.bd.blooddonorfinder.service.NotificationService;
import com.bd.blooddonorfinder.service.es.UserElasticSearchService;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Urgent-request escalation (P0 product loop):
 *
 * <ol>
 *   <li><b>Expiry</b> — every PENDING request past its urgency TTL becomes
 *       EXPIRED in one bulk statement.</li>
 *   <li><b>Escalation</b> — unanswered URGENT/CRITICAL requests are
 *       re-broadcast to nearby donors on a widening radius ladder
 *       (e.g. 10 → 25 → 50 → 80 km), each donor alerted at most once per
 *       request (jsonb dedupe), with a per-escalation recipient cap.</li>
 * </ol>
 *
 * Escalation cadence comes from {@code app.escalation.interval-minutes.<urgency>}.
 * ShedLock guarantees a single sweeper; the level check makes replays harmless.
 */
@Component
@Slf4j
public class RequestEscalationWorker {

    private static final int MAX_ESCALATIONS = 4;

    private final BloodRequestRepository bloodRequestRepository;
    private final UserElasticSearchService userElasticSearchService;
    private final NotificationService notificationService;

    @Value("${app.escalation.radii-km:10,25,50,80}")
    private List<Double> escalationRadiiKm;

    @Value("${app.escalation.interval-minutes.urgent:60}")
    private long urgentIntervalMinutes;

    @Value("${app.escalation.interval-minutes.critical:15}")
    private long criticalIntervalMinutes;

    @Value("${app.escalation.max-donors-per-escalation:50}")
    private int maxDonorsPerEscalation;

    @Value("${app.escalation.batch-size:50}")
    private int batchSize;

    public RequestEscalationWorker(BloodRequestRepository bloodRequestRepository,
                                   UserElasticSearchService userElasticSearchService,
                                   NotificationService notificationService) {
        this.bloodRequestRepository = bloodRequestRepository;
        this.userElasticSearchService = userElasticSearchService;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${app.escalation.cron:0 * * * * *}")
    @SchedulerLock(name = "requestEscalationWorker", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
    public void sweep() {
        LockAssert.assertLocked();

        int expired = bloodRequestRepository.expireStaleRequests(LocalDateTime.now());
        if (expired > 0) {
            log.info("Expired {} unanswered blood request(s) past their TTL", expired);
        }
        escalateUrgentRequests();
    }

    private void escalateUrgentRequests() {
        for (RequestUrgency urgency : List.of(RequestUrgency.CRITICAL, RequestUrgency.URGENT)) {
            LocalDateTime cutoff = LocalDateTime.now()
                    .minusMinutes(intervalMinutes(urgency));
            List<BloodRequest> candidates = bloodRequestRepository.findEscalationCandidates(
                    cutoff, MAX_ESCALATIONS, PageRequest.of(0, batchSize));
            for (BloodRequest request : candidates) {
                try {
                    escalate(request);
                } catch (Exception e) {
                    log.error("Escalation failed for request={}: {}", request.getId(), e.getMessage(), e);
                }
            }
        }
    }

    private void escalate(BloodRequest request) {
        int level = request.getEscalationLevel();
        double radiusKm = radiusForLevel(level);
        if (request.getGeoLocation() == null
                || request.getGeoLocation().getLatitude() == null) {
            return; // nothing to broadcast from
        }

        Set<String> alreadyAlerted = request.getAlertedUserIds() != null
                ? new HashSet<>(request.getAlertedUserIds()) : new HashSet<>();

        NearbyDonorSearchResponse response = userElasticSearchService.searchNearbyDonors(
                nearbyRequest(request, radiusKm, alreadyAlerted));
        if (response.getDonors() == null || response.getDonors().isEmpty()) {
            bloodRequestRepository.applyEscalation(request.getId(),
                    new ArrayList<>(alreadyAlerted), LocalDateTime.now());
            return;
        }

        List<String> newlyAlerted = new ArrayList<>();
        double requesterLat = request.getGeoLocation().getLatitude().doubleValue();
        double requesterLon = request.getGeoLocation().getLongitude().doubleValue();
        for (DonorSearchDocument donor : response.getDonors()) {
            if (newlyAlerted.size() >= maxDonorsPerEscalation) {
                break;
            }
            UUID donorId = parseId(donor.getId());
            if (donorId == null || alreadyAlerted.contains(donor.getId())) {
                continue;
            }
            long distanceKm = distanceKm(requesterLat, requesterLon, donor);
            notificationService.notify(donorId, NotificationType.URGENT_REQUEST_ALERT,
                    request.getId(), Map.of(
                            "requestId", request.getId().toString(),
                            "bloodGroup", request.getBloodGroup().name(),
                            "urgency", request.getUrgency().name(),
                            "distanceKm", distanceKm));
            newlyAlerted.add(donor.getId());
        }

        alreadyAlerted.addAll(newlyAlerted);
        bloodRequestRepository.applyEscalation(request.getId(),
                new ArrayList<>(alreadyAlerted), LocalDateTime.now());
        log.info("Escalated request={} to level={} radius={}km — alerted {} donor(s)",
                request.getId(), level + 1, radiusKm, newlyAlerted.size());
    }

    private com.bd.blooddonorfinder.payload.request.NearbyDonorSearchRequest nearbyRequest(
            BloodRequest request, double radiusKm, Set<String> alreadyAlerted) {
        var searchRequest = new com.bd.blooddonorfinder.payload.request.NearbyDonorSearchRequest();
        searchRequest.setLatitude(request.getGeoLocation().getLatitude().doubleValue());
        searchRequest.setLongitude(request.getGeoLocation().getLongitude().doubleValue());
        searchRequest.setBloodGroup(request.getBloodGroup().name());
        searchRequest.setIsAvailable(true);
        // single-pass query at the ladder radius — the worker owns the expansion
        searchRequest.setInitialRadiusKm(radiusKm);
        searchRequest.setRadiusStepKm(radiusKm);
        searchRequest.setMaxExpansions(0);
        searchRequest.setLength(maxDonorsPerEscalation + alreadyAlerted.size());
        return searchRequest;
    }

    private double radiusForLevel(int level) {
        // ladder: level 0 → radii[0], capped at the last entry
        int index = Math.min(level, escalationRadiiKm.size() - 1);
        return escalationRadiiKm.get(index);
    }

    private long intervalMinutes(RequestUrgency urgency) {
        return urgency == RequestUrgency.CRITICAL ? criticalIntervalMinutes : urgentIntervalMinutes;
    }

    private static UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Haversine distance in km — the ES response does not carry sort values. */
    private static long distanceKm(double lat1, double lon1, DonorSearchDocument donor) {
        if (donor.getLocation() == null) {
            return 0;
        }
        double lat2 = donor.getLocation().getLatitude();
        double lon2 = donor.getLocation().getLongitude();
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(dLon / 2), 2);
        return Math.round(2 * 6371 * Math.asin(Math.sqrt(a)));
    }
}
