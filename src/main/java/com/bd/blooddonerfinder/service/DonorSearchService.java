package com.bd.blooddonerfinder.service;

import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.model.enums.BloodGroup;
import com.bd.blooddonerfinder.payload.request.DonorSearchRequest;
import org.springframework.stereotype.Service;

import java.util.List;


public interface DonorSearchService {
    List<User> findNearByDonors (DonorSearchRequest searchRequest);
}
