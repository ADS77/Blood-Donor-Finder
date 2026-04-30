package com.bd.blooddonerfinder.service;

import com.bd.blooddonerfinder.exception.ResourceNotFoundException;
import com.bd.blooddonerfinder.model.BloodRequest;
import com.bd.blooddonerfinder.model.DonationHistory;
import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.repository.BloodRequestRepository;
import com.bd.blooddonerfinder.repository.DonationHistoryRepository;
import com.bd.blooddonerfinder.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DonationHistoryServiceImpl implements DonationHistoryService {

    private final DonationHistoryRepository donationHistoryRepository;
    private final UserRepository userRepository;
    private final BloodRequestRepository bloodRequestRepository;

    private static final int ELIGIBILITY_DAYS = 120; // 4 months

    @Override
    @Transactional
    public DonationHistory recordDonation(Long donorId, Long recipientId, Long requestId, String notes) {
        User donor = userRepository.findById(donorId)
                .orElseThrow(() -> new ResourceNotFoundException("Donor not found"));
        
        User recipient = null;
        if (recipientId != null) {
            recipient = userRepository.findById(recipientId).orElse(null);
        }

        BloodRequest request = null;
        if (requestId != null) {
            request = bloodRequestRepository.findById(requestId).orElse(null);
        }

        DonationHistory history = new DonationHistory();
        history.setDonor(donor);
        history.setRecipient(recipient);
        history.setRequest(request);
        history.setDonationDate(LocalDateTime.now());
        history.setNotes(notes);
        history.setVerified(true);

        // Update donor's last donation date
        donor.setLastDonationDate(LocalDate.now());
        userRepository.save(donor);

        return donationHistoryRepository.save(history);
    }

    @Override
    public List<DonationHistory> getDonorHistory(Long donorId) {
        User donor = userRepository.findById(donorId)
                .orElseThrow(() -> new ResourceNotFoundException("Donor not found"));
        return donationHistoryRepository.findByDonorOrderByDonationDateDesc(donor);
    }

    @Override
    public boolean isEligibleToDonate(User donor) {
        if (donor.getLastDonationDate() == null) {
            return true;
        }
        long daysSinceLastDonation = ChronoUnit.DAYS.between(donor.getLastDonationDate(), LocalDate.now());
        return daysSinceLastDonation >= ELIGIBILITY_DAYS;
    }
}
