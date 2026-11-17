package com.bd.blooddonorfinder.controller;

import com.bd.blooddonorfinder.model.common.GeoLocation;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.GeoStatus;
import com.bd.blooddonorfinder.payload.request.LocationUpdateRequest;
import com.bd.blooddonorfinder.payload.request.UpdateProfileRequest;
import com.bd.blooddonorfinder.payload.response.LocationUpdateResponse;
import com.bd.blooddonorfinder.security.UserPrincipal;
import com.bd.blooddonorfinder.service.LocationIngestService;
import com.bd.blooddonorfinder.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Objects;

@RestController
@RequestMapping("/api/user")
@Slf4j
public class UserController {

    private final UserService userService;
    private final LocationIngestService locationIngestService;

    public UserController(UserService userService,
                          LocationIngestService locationIngestService) {
        this.userService = userService;
        this.locationIngestService = locationIngestService;
    }

    /**
     * Updates the profile of the authenticated user.
     *
     * <p>The target account is always derived from the verified JWT principal,
     * never from a client-supplied identifier. This prevents an authenticated
     * donor from modifying another donor's profile (IDOR) or granting
     * themselves roles (privilege escalation).</p>
     */
    @PutMapping("/me")
    public ResponseEntity<String> updateCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {

        User existingUser = userService.findById(principal.getId());

        if (request.getFirstName() != null) {
            existingUser.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            existingUser.setLastName(request.getLastName());
        }
        if (request.getBloodGroup() != null) {
            existingUser.setBloodGroup(request.getBloodGroup());
        }
        if (request.getIsAvailable() != null) {
            existingUser.setIsAvailable(request.getIsAvailable());
        }
        if (request.getLastDonationDate() != null) {
            existingUser.setLastDonationDate(request.getLastDonationDate());
        }

        boolean coordinatesChanged = false;
        if (request.getGeoLocation() != null) {
            GeoLocation previousGeo = existingUser.getGeoLocation();
            Instant previousStamp = previousGeo != null ? previousGeo.getGeoUpdatedAt() : null;
            Instant previousReconfirm = previousGeo != null ? previousGeo.getGeoReconfirmRequestedAt() : null;
            GeoStatus previousStatus = previousGeo != null ? previousGeo.getGeoStatus() : null;
            coordinatesChanged = hasNewCoordinates(previousGeo, request.getGeoLocation());
            existingUser.setGeoLocation(request.getGeoLocation());
            if (coordinatesChanged) {
                // Location-freshness pipeline: a real coordinate change through the
                // profile endpoint renews the freshness stamp and re-enters ES sync.
                existingUser.getGeoLocation().setGeoUpdatedAt(Instant.now());
                existingUser.getGeoLocation().setGeoReconfirmRequestedAt(null);
                existingUser.getGeoLocation().setGeoStatus(GeoStatus.PENDING);
            } else {
                // Preserve server-owned state — a client-supplied geoUpdatedAt
                // would let donors spoof freshness forever, and a client-supplied
                // geoStatus would let them pre-confirm an unverified location.
                existingUser.getGeoLocation().setGeoUpdatedAt(previousStamp);
                existingUser.getGeoLocation().setGeoReconfirmRequestedAt(previousReconfirm);
                existingUser.getGeoLocation().setGeoStatus(previousStatus);
            }
        }

        userService.saveUser(existingUser);
        if (coordinatesChanged) {
            locationIngestService.propagateLocationChange(existingUser);
        }
        log.debug("Updated profile for userId={}", existingUser.getId());
        return ResponseEntity.ok().body("user updated");
    }

    /**
     * Kept as a backwards-compatible alias, but it now ignores any identifier
     * in the body and only ever updates the caller's own account.
     */
    @PostMapping("/update")
    public ResponseEntity<String> updateUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return updateCurrentUser(principal, request);
    }

    /**
     * Location freshness pipeline (write side): the donor's device reports its
     * current position. Throttled + deduplicated; coordinates flow to
     * Elasticsearch asynchronously via USER_LOCATION_UPDATED.
     */
    @PostMapping("/location")
    public ResponseEntity<String> updateLocation(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LocationUpdateRequest request) {
        // SECURITY: principal-based — a donor can only ever update their own location
        LocationUpdateResponse result = locationIngestService.updateLocation(principal.getId(), request);
        return ResponseEntity.ok().body(result.message());
    }

    /** True when the incoming profile carries different (and usable) coordinates. */
    private static boolean hasNewCoordinates(GeoLocation current, GeoLocation incoming) {
        if (incoming == null || incoming.getLatitude() == null || incoming.getLongitude() == null) {
            return false;
        }
        return current == null
                || current.getLatitude() == null || current.getLongitude() == null
                || !Objects.equals(current.getLatitude(), incoming.getLatitude())
                || !Objects.equals(current.getLongitude(), incoming.getLongitude());
    }
}
