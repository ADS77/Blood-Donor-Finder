package com.bd.blooddonorfinder.repository;

import com.bd.blooddonorfinder.model.donation.DonationRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DonationRecordRepository extends JpaRepository<DonationRecord, UUID> {

    /** Donor's donation history, newest first (keyset via donatedAt cursor). */
    @Query("""
           SELECT d FROM DonationRecord d
           WHERE d.donor.id = :donorId
             AND (:before IS NULL OR d.donatedAt < :before)
           ORDER BY d.donatedAt DESC
           """)
    List<DonationRecord> findDonorHistory(@Param("donorId") UUID donorId,
                                          @Param("before") Instant before,
                                          Pageable pageable);

    Optional<DonationRecord> findByRequestId(UUID requestId);
}
