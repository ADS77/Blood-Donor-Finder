package com.bd.blooddonerfinder.repository;

import com.bd.blooddonerfinder.model.BloodRequest;
import com.bd.blooddonerfinder.payload.request.BloodRequestDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BloodRequestRepository extends JpaRepository<BloodRequest,Long> {
}
