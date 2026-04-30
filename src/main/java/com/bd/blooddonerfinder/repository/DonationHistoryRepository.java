package com.bd.blooddonerfinder.repository;

import com.bd.blooddonerfinder.model.DonationHistory;
import com.bd.blooddonerfinder.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DonationHistoryRepository extends JpaRepository<DonationHistory, Long> {
    List<DonationHistory> findByDonorOrderByDonationDateDesc(User donor);
}
