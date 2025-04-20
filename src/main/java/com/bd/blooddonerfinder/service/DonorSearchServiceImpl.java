package com.bd.blooddonerfinder.service;

import com.bd.blooddonerfinder.model.Location;
import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.model.enums.BloodGroup;
import com.bd.blooddonerfinder.model.enums.Role;
import com.bd.blooddonerfinder.repository.UserRepository;
import com.bd.blooddonerfinder.util.GeoUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
@Service
public class DonorSearchServiceImpl implements DonorSearchService{
    private final UserRepository userRepository;

    public DonorSearchServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<User> findNearByDonors(double lat, double lon, BloodGroup bloodGroup, double radiusKm) {
        List<User> allDonors = userRepository.findNearByDonors(Role.DONOR, bloodGroup);
        List<User> nearByDonors = new ArrayList<>();
        for(User user : allDonors){
            Location location = user.getLocation();
            if (location == null || location.getLatitude() == null || location.getLongitude() == null){
                continue;
            }
            double distance = GeoUtils.haversine(lat,location.getLatitude(), lon, location.getLongitude());
            if(distance <= radiusKm){
                nearByDonors.add(user);
            }
        }
        return nearByDonors;
    }
}
