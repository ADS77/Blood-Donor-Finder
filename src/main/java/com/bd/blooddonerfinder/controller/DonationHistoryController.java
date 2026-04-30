package com.bd.blooddonerfinder.controller;

import com.bd.blooddonerfinder.model.DonationHistory;
import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.repository.UserRepository;
import com.bd.blooddonerfinder.service.DonationHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/donations")
@RequiredArgsConstructor
public class DonationHistoryController {

    private final DonationHistoryService donationHistoryService;
    private final UserRepository userRepository;

    @PostMapping("/record")
    public ResponseEntity<DonationHistory> recordDonation(@RequestBody Map<String, Object> body) {
        Long donorId = Long.valueOf(body.get("donorId").toString());
        Long recipientId = body.get("recipientId") != null ? Long.valueOf(body.get("recipientId").toString()) : null;
        Long requestId  = body.get("requestId")  != null ? Long.valueOf(body.get("requestId").toString())  : null;
        String notes = body.get("notes") != null ? body.get("notes").toString() : null;
        return ResponseEntity.ok(donationHistoryService.recordDonation(donorId, recipientId, requestId, notes));
    }

    @GetMapping("/history/{donorId}")
    public ResponseEntity<List<DonationHistory>> getDonorHistory(@PathVariable Long donorId) {
        return ResponseEntity.ok(donationHistoryService.getDonorHistory(donorId));
    }

    @GetMapping("/eligible/{donorId}")
    public ResponseEntity<Map<String, Object>> checkEligibility(@PathVariable Long donorId) {
        User donor = userRepository.findById(donorId)
                .orElseThrow(() -> new RuntimeException("Donor not found with id: " + donorId));
        boolean eligible = donationHistoryService.isEligibleToDonate(donor);
        return ResponseEntity.ok(Map.of(
                "donorId", donorId,
                "eligible", eligible,
                "lastDonationDate", donor.getLastDonationDate() != null ? donor.getLastDonationDate().toString() : "Never donated"
        ));
    }
}
