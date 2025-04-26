package com.bd.blooddonerfinder.service;

import com.bd.blooddonerfinder.model.Location;
import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.model.enums.BloodGroup;
import com.bd.blooddonerfinder.model.enums.Role;
import com.bd.blooddonerfinder.payload.request.SendMailRequest;
import com.bd.blooddonerfinder.repository.UserRepository;
import com.bd.blooddonerfinder.util.GeoUtils;
import com.bd.blooddonerfinder.util.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
@Service
@Slf4j
public class DonorSearchServiceImpl implements DonorSearchService{
    private final UserRepository userRepository;
    private final MailService mailService;

    public DonorSearchServiceImpl(UserRepository userRepository,
                                  MailService mailService) {
        this.userRepository = userRepository;
        this.mailService = mailService;
    }

    @Override
    public List<User> findNearByDonors(double lat, double lon, BloodGroup bloodGroup, double radiusKm) {
        List<User> allDonors = userRepository.findNearByRoleAndBloodGroup(Role.DONOR, bloodGroup);
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
        sendNotification(nearByDonors);
        return nearByDonors;
    }

    private void sendNotification(List<User> nearByDonors) {
        for(User donor : nearByDonors){
            if(Utils.isValidEmail(donor.getEmail())){
                SendMailRequest mailRequest = new SendMailRequest(
                        donor.getEmail(),
                        "Looking for bllod",
                        "blood needed at location : " + donor.getLocation().toString());
                mailService.sendMail(mailRequest.getMailTo(),
                        mailRequest.getSubject(),
                        mailRequest.getBody());
            }
            else {
                log.error("Invalid Email");
            }

        }
    }
}
