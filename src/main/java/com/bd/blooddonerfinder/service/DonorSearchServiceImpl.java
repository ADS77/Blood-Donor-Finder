package com.bd.blooddonerfinder.service;

import com.bd.blooddonerfinder.model.Location;
import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.model.enums.Role;
import com.bd.blooddonerfinder.payload.request.DonorSearchRequest;
import com.bd.blooddonerfinder.repository.UserRepository;
import com.bd.blooddonerfinder.util.GeoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DonorSearchServiceImpl implements DonorSearchService{
    private final UserRepository userRepository;

    public DonorSearchServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;

    }

    @Override
    public List<User> findNearByDonors(DonorSearchRequest searchRequest) {
        List<User> allDonors = userRepository.findNearByRoleAndBloodGroup(Role.DONOR, searchRequest.getBloodGroup());
        List<User> nearByDonors = new ArrayList<>();
        for(User user : allDonors){
            Location location = user.getLocation();
            if (location == null || location.getLatitude() == null || location.getLongitude() == null){
                continue;
            }
            double distance = GeoUtils.haversine(
                    searchRequest.getLocation().getLatitude(),
                    location.getLatitude(),
                    searchRequest.getLocation().getLongitude(),
                    location.getLongitude());
            if(distance <= searchRequest.getRadius()){
                nearByDonors.add(user);
            }
        }
        return nearByDonors;
    }

}
