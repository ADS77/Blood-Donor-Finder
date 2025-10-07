package com.bd.blooddonerfinder.service;

import com.bd.blooddonerfinder.model.GeoLocation;
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
            GeoLocation geoLocation = user.getGeoLocation();
            if (geoLocation == null || geoLocation.getLatitude() == null || geoLocation.getLongitude() == null){
                continue;
            }
            double distance = GeoUtils.haversine(
                    searchRequest.getGeoLocation().getLatitude(),
                    geoLocation.getLatitude(),
                    searchRequest.getGeoLocation().getLongitude(),
                    geoLocation.getLongitude());
            if(distance <= searchRequest.getRadius()){
                nearByDonors.add(user);
            }
        }
        return nearByDonors;
    }

}
