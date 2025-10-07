package com.bd.blooddonerfinder.service;

import com.bd.blooddonerfinder.model.BloodRequest;
import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.model.enums.RequestStatus;
import com.bd.blooddonerfinder.payload.request.BloodRequestDto;
import com.bd.blooddonerfinder.payload.response.RestApiResponse;
import com.bd.blooddonerfinder.repository.BloodRequestRepository;
import com.bd.blooddonerfinder.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
public class BloodRequestServiceImpl implements  BloodRequestService{
    private final BloodRequestRepository bloodRequestRepository;
    private final UserRepository userRepository;

    public BloodRequestServiceImpl(BloodRequestRepository bloodRequestRepository, UserRepository userRepository) {
        this.bloodRequestRepository = bloodRequestRepository;
        this.userRepository = userRepository;
    }

    @Override
    public RestApiResponse<BloodRequest> createBloodRequest(BloodRequestDto bloodRequestDto) {
        Optional<User> optionalRequester = Optional.ofNullable(userRepository.findById(bloodRequestDto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found")));
        BloodRequest bloodRequest = new BloodRequest();
        RestApiResponse<BloodRequest> response = new RestApiResponse<>();
        if(optionalRequester.isPresent()){
            try {
                bloodRequest.setRequester(optionalRequester.get());
                bloodRequest.setNeededGroup(bloodRequestDto.getNeededBloodGroup());
                bloodRequest.setGeoLocation(bloodRequestDto.getGeoLocation());
                bloodRequest.setRequiredQuantity(bloodRequestDto.getQuantity());
                bloodRequest.setStatus(RequestStatus.PENDING);
                bloodRequest.setRequestTime(LocalDateTime.now());
                bloodRequest.setMessage(bloodRequestDto.getMessage());

                bloodRequestRepository.save(bloodRequest);
                log.debug("Blood Request Created");
                response.setData(bloodRequest);
                response.setMessage("Blood Request Created");
                response.setStatus(HttpStatus.OK);
            }
            catch (Exception e){
                log.error("Failed to process blood request: {}", e.getMessage());
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
                response.setMessage("Failed to process blood request");
            }
        }
        return  response;
    }
}
