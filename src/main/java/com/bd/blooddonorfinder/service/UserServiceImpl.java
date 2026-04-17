package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.kafka.model.events.UserRegisteredEvent;
import com.bd.blooddonorfinder.kafka.producer.GenericKafkaEventProducer;
import com.bd.blooddonorfinder.model.User;
import com.bd.blooddonorfinder.payload.request.UserRegistrationRequest;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.repository.UserRepository;
import com.bd.blooddonorfinder.utils.Utils;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserServiceImpl implements UserService{
    private final UserRepository userRepository;
    private final GenericKafkaEventProducer eventProducer;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, GenericKafkaEventProducer eventProducer, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.eventProducer = eventProducer;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public RestApiResponse<User> registerUser(UserRegistrationRequest registrationRequest) {
        log.debug("Registering new user : name = {}", registrationRequest.getName());
        RestApiResponse<User> apiResponse;

        if (registrationRequest == null) {
            apiResponse = Utils.buildErrorRestResponse(HttpStatus.BAD_REQUEST, "reqRequest", "Registration request must not be null");
        }

            boolean emailExists = userRepository.existsByEmail(registrationRequest.getEmail());
            boolean phoneExists = userRepository.existsByPhone(registrationRequest.getPhone());
            if (emailExists || phoneExists) {
                String message = emailExists && phoneExists ? "Email and Phone number already in use"
                                : emailExists
                                ? "Email already in use"
                                : "Phone number already in use";
                apiResponse = Utils.buildErrorRestResponse(HttpStatus.CONFLICT, "email/password", message);
            }
            try {
                String encodedPassword = passwordEncoder.encode(registrationRequest.getPassword());
                User newUser = User.of(registrationRequest, encodedPassword);
                User savedUser = userRepository.save(newUser);
                log.debug("User saved to db: userId = {}, name = {}", savedUser.getId(), savedUser.getName());

                // Save these user in elastic
                UserRegisteredEvent event = UserRegisteredEvent.from(savedUser);
                eventProducer.publishEvent(event);
                log.debug("Published user-registered event for userId={}", savedUser.getId());
                apiResponse = Utils.buildSuccessRestResponse(HttpStatus.CREATED,"Registration successful", savedUser);
            } catch (Exception e) {
            log.error("Registration failed for email={}: {}", registrationRequest.getEmail(), e.getMessage(), e);
            apiResponse = Utils.buildErrorRestResponse(HttpStatus.INTERNAL_SERVER_ERROR, "","Registration failed");
        }

        return apiResponse;
    }

}
