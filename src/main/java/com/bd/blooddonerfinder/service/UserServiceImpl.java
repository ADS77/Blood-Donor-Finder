package com.bd.blooddonerfinder.service;

import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.payload.request.UserRegistrationRequest;
import com.bd.blooddonerfinder.payload.response.RestApiResponse;
import com.bd.blooddonerfinder.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
public class UserServiceImpl implements UserService{
    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public RestApiResponse<User> registerUser(UserRegistrationRequest registrationRequest) {
        RestApiResponse<User> apiResponse = new RestApiResponse<>();
        if(registrationRequest != null){
            try {
                Optional<User> optionalUser = userRepository.findByEmail(registrationRequest.getEmail());
                if(optionalUser.isEmpty()){
                    User newUser = new User(
                            registrationRequest.getName(),
                            registrationRequest.getEmail(),
                            registrationRequest.getPhone(),
                            registrationRequest.getBloodGroup(),
                            registrationRequest.getRole(),
                            registrationRequest.getLocation()
                    );
                    newUser.setCreatedAt(LocalDateTime.now());
                    newUser.setIsVerified(false);
                    newUser.setIsAvailable(true);
                    userRepository.save(newUser);
                    log.debug("Registration successful");
                    apiResponse.setData(newUser);
                    apiResponse.setMessage("Registration successful");
                    apiResponse.setStatus(HttpStatus.OK);
                }
                else {
                    apiResponse.setData(optionalUser.get());
                    apiResponse.setMessage("User already exists");
                    apiResponse.setStatus(HttpStatus.CONFLICT);
                }
            }catch (Exception e){
                log.error("Error while registering user : {}", e.getMessage());
                apiResponse.setMessage("Registration Failed");
                apiResponse.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
            }

        }else {
            apiResponse.setData(null);
            apiResponse.setStatus(HttpStatus.BAD_REQUEST);
            apiResponse.setMessage("Registration failed (reg request is null) ");
        }
        return  apiResponse;
    }
}
