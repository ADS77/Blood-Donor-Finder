package com.bd.blooddonorfinder.controller;

import com.bd.blooddonorfinder.payload.response.DonorDetailDto;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.service.UserService;
import com.bd.blooddonorfinder.utils.Utils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Donor-facing read endpoints. The detail view is deliberately narrow: no
 * contact details, no exact coordinates — conversations happen through chat and
 * only after a request is accepted.
 */
@RestController
@RequestMapping("/api/donors")
public class DonorController {

    private final UserService userService;

    public DonorController(UserService userService) {
        this.userService = userService;
    }

    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @GetMapping("/{donorId}")
    public ResponseEntity<RestApiResponse<DonorDetailDto>> getDonor(@PathVariable UUID donorId) {
        return ResponseEntity.ok(Utils.buildSuccessRestResponse(HttpStatus.OK,
                userService.getDonorDetail(donorId)));
    }
}
