package com.bd.blooddonorfinder.controller;

import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.payload.request.UpdateProfileRequest;
import com.bd.blooddonorfinder.security.UserPrincipal;
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

@RestController
@RequestMapping("/api/user")
@Slf4j
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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
        if (request.getGeoLocation() != null) {
            existingUser.setGeoLocation(request.getGeoLocation());
        }
        if (request.getLastDonationDate() != null) {
            existingUser.setLastDonationDate(request.getLastDonationDate());
        }

        userService.saveUser(existingUser);
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
}
