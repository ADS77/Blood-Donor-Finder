package com.bd.blooddonerfinder.service;

import com.bd.blooddonerfinder.model.DonationHistory;
import com.bd.blooddonerfinder.model.User;
import java.util.List;

public interface DonationHistoryService {
    DonationHistory recordDonation(Long donorId, Long recipientId, Long requestId, String notes);
    List<DonationHistory> getDonorHistory(Long donorId);
    boolean isEligibleToDonate(User donor);
}
