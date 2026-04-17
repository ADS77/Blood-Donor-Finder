package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.model.User;
import com.bd.blooddonorfinder.payload.request.DonorSearchRequest;

import java.util.List;


public interface DonorSearchService {
    List<User> findNearByDonors (DonorSearchRequest searchRequest);
    List<User> findNearByEligibleDonors(DonorSearchRequest searchRequest);
}
