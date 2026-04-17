package com.bd.blooddonorfinder.repository;

import com.bd.blooddonorfinder.model.BloodRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BloodRequestRepository extends JpaRepository<BloodRequest,Long> {
}
