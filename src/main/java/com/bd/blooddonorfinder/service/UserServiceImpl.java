package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import com.bd.blooddonorfinder.kafka.model.events.UserRegisteredEvent;
import com.bd.blooddonorfinder.kafka.producer.GenericKafkaEventProducer;
import com.bd.blooddonorfinder.model.GeoLocation;
import com.bd.blooddonorfinder.model.GeoResponse;
import com.bd.blooddonorfinder.model.User;
import com.bd.blooddonorfinder.model.enums.GeoStatus;
import com.bd.blooddonorfinder.payload.request.UserRegistrationRequest;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.repository.UserRepository;
import com.bd.blooddonorfinder.utils.Utils;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Slf4j
public class UserServiceImpl implements UserService{
    private final UserRepository userRepository;
    private final GenericKafkaEventProducer eventProducer;
    private final PasswordEncoder passwordEncoder;
    private final GeoLocationService geoLocationService;

    public UserServiceImpl(UserRepository userRepository, GenericKafkaEventProducer eventProducer, PasswordEncoder passwordEncoder, GeoLocationService geoLocationService) {
        this.userRepository = userRepository;
        this.eventProducer = eventProducer;
        this.passwordEncoder = passwordEncoder;
        this.geoLocationService = geoLocationService;
    }

    @Override
    @Transactional
    public RestApiResponse<User> registerUser(UserRegistrationRequest registrationRequest) {
        if (registrationRequest == null) {
            return Utils.buildErrorRestResponse(HttpStatus.BAD_REQUEST, "regRequest", "Registration request must not be null");
        }
        log.debug("Registering new user : name = {}", registrationRequest.getName());

        boolean emailExists = userRepository.existsByEmail(registrationRequest.getEmail());
        boolean phoneExists = userRepository.existsByPhone(registrationRequest.getPhone());
            if (emailExists || phoneExists) {
                String message = emailExists && phoneExists ? "Email and Phone number already in use"
                                : emailExists ? "Email already in use"
                                : "Phone number already in use";
                log.info("Duplicate found : "+message);
                return Utils.buildErrorRestResponse(HttpStatus.CONFLICT, "email/password", message);
            }
            try {
                String encodedPassword = passwordEncoder.encode(registrationRequest.getPassword());
                User newUser = User.of(registrationRequest, encodedPassword);
                GeoLocation geo = newUser.getGeoLocation();
                if(geo != null && geo.getCity() != null &&
                        (geo.getLatitude() == null || geo.getLongitude() == null)){
                    newUser.getGeoLocation().setGeoStatus(GeoStatus.PENDING);
                }

                User savedUser = userRepository.save(newUser);
                log.info("User saved to db: userId = {}, name = {}", savedUser.getId(), savedUser.getName());

                UserRegisteredEvent event = UserRegisteredEvent.from(savedUser);
                publishAfterCommit(event);
                log.info("Published user-registered event for userId={}", savedUser.getId());
                return Utils.buildSuccessRestResponse(HttpStatus.CREATED,"Registration successful", savedUser);

            } catch (DataIntegrityViolationException e) {
                log.warn("Unique constraint violation during registration for email={}: {}",
                        registrationRequest.getEmail(), e.getMessage());
                return Utils.buildErrorRestResponse(HttpStatus.CONFLICT, "email/password", "Email or phone number already in use");
            } catch (Exception e) {
                log.error("Registration failed for username={}: {}", registrationRequest.getName(), e.getMessage(), e);
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Utils.buildErrorRestResponse(HttpStatus.INTERNAL_SERVER_ERROR, "","Registration failed");
        }
    }

    private User getGeoEnhancedUser(User newUser) {
           GeoResponse geoResponse = geoLocationService.getLatLong(newUser.getGeoLocation().getCity());
           if(geoResponse.isSuccess()){
               log.info("Updating user Geo location : {}", geoResponse);
               newUser.getGeoLocation().setLongitude(geoResponse.getLongitude());
               newUser.getGeoLocation().setLatitude(geoResponse.getLatitude());
           }
       return newUser;
    }

    private void publishAfterCommit(BaseEvent event) {
        if(TransactionSynchronizationManager.isSynchronizationActive()){
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            eventProducer.publishEvent(event);
                            log.info("Published event after commit: eventId={}, aggregateId={}",
                                    event.getEventId(), event.getAggregateId());
                        }
                    }
            );
        }
        else {
            eventProducer.publishEvent(event);
        }
    }

}
