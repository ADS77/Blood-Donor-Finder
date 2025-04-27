package com.bd.blooddonerfinder.service;

import com.bd.blooddonerfinder.model.Location;
import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.model.enums.BloodGroup;
import com.bd.blooddonerfinder.model.enums.Role;
import com.bd.blooddonerfinder.payload.request.DonorSearchRequest;
import com.bd.blooddonerfinder.payload.request.MailAttachment;
import com.bd.blooddonerfinder.payload.request.SendMailRequest;
import com.bd.blooddonerfinder.repository.UserRepository;
import com.bd.blooddonerfinder.util.GeoUtils;
import com.bd.blooddonerfinder.util.MailUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        sendNotification(nearByDonors,searchRequest);
        return nearByDonors;
    }

    private void sendNotification(List<User> nearByDonors, DonorSearchRequest searchRequest) {
        for(User donor : nearByDonors){
            if(MailUtils.isValidEmail(donor.getEmail())){
                String donorName = donor.getName();
                SendMailRequest mailRequest = new SendMailRequest(
                        donor.getEmail(),
                        searchRequest.getReceiverEmail(),
                        "Looking for blood",
                        MailUtils.buildHtmlBody(
                                donorName,
                                searchRequest.getLocation().toString(),
                                searchRequest.getReceiverPhone(),
                                "DREAM"
                                ));
                mailRequest.setHtmlContent(true);
                mailService.sendMail(mailRequest);
            }
            else {
                log.error("Invalid Email");
            }

        }
    }
}
