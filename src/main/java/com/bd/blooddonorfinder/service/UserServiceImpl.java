package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.exception.AuthException;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.exception.UserNotFoundException;
import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import com.bd.blooddonorfinder.kafka.model.events.UserRegisteredEvent;
import com.bd.blooddonorfinder.kafka.producer.GenericKafkaEventProducer;
import com.bd.blooddonorfinder.model.common.GeoLocation;
import com.bd.blooddonorfinder.model.PiiToken;
import com.bd.blooddonorfinder.model.common.Role;
import com.bd.blooddonorfinder.model.enums.AuditEvent;
import com.bd.blooddonorfinder.model.enums.OtpPurpose;
import com.bd.blooddonorfinder.model.enums.PiiType;
import com.bd.blooddonorfinder.payload.response.*;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.GeoStatus;
import com.bd.blooddonorfinder.payload.request.RegisterRequest;
import com.bd.blooddonorfinder.repository.UserRepository;
import com.bd.blooddonorfinder.repository.auth.RoleRepository;
import com.bd.blooddonorfinder.security.AppPermissions;
import com.bd.blooddonorfinder.service.audit.AuditService;
import com.bd.blooddonorfinder.utils.PiiTokenizer;
import com.bd.blooddonorfinder.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class UserServiceImpl implements UserService{
    private final UserRepository userRepository;
    private final GenericKafkaEventProducer eventProducer;
    private final PasswordEncoder passwordEncoder;
    private final GeoLocationService geoLocationService;
    private final PiiTokenizer piiTokenizer;
    private final RoleRepository roleRepository;
    private final OtpService otpService;
    private final AuditService auditService;

    public UserServiceImpl(UserRepository userRepository,
                           GenericKafkaEventProducer eventProducer,
                           PasswordEncoder passwordEncoder,
                           GeoLocationService geoLocationService,
                           PiiTokenizer piiTokenizer,
                           RoleRepository roleRepository,
                           OtpService otpService,
                           AuditService auditService) {
        this.userRepository = userRepository;
        this.eventProducer = eventProducer;
        this.passwordEncoder = passwordEncoder;
        this.geoLocationService = geoLocationService;
        this.piiTokenizer = piiTokenizer;
        this.roleRepository = roleRepository;
        this.otpService = otpService;
        this.auditService = auditService;
    }

    private final String ROLE_DEFAULT = AppPermissions.ROLE_DONOR;

    @Override
    @Transactional
    public User findById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new AuthException(ErrorCode.USER_NOT_FOUND, "User not found"));
    }

    @Override
    public OtpSentResponse registerNewUser(RegisterRequest registerRequest, String ipAddress, String userAgent) {
        if(piiTokenizer.exists(registerRequest.getEmail())){
            piiTokenizer.findByPlaintext(registerRequest.getEmail()).ifPresent(token -> {
                        if(existByEmailToken(token)){
                            throw new AuthException(ErrorCode.USER_ALREADY_EXISTS_WITH_EMAIL,
                                    "A user with this email already exists");
                        }
                    }
            );
        }
        if(piiTokenizer.exists(registerRequest.getPhone())){
            piiTokenizer.findByPlaintext(registerRequest.getPhone()).ifPresent(token->{
                if(existByPhoneToken(token)){
                    throw new AuthException(ErrorCode.USER_ALREADY_EXISTS_WITH_PHONE,
                            "A user with this phone number already exists");
                }
            });
        }
        var phoneToken = piiTokenizer.findOrCreate(registerRequest.getPhone(), PiiType.PHONE);
        var emailToken = piiTokenizer.findOrCreate(registerRequest.getEmail(), PiiType.EMAIL);
        String encodedPass = passwordEncoder.encode(registerRequest.getPassword());
        User newUser = User.of(registerRequest, encodedPass, phoneToken, emailToken);
        newUser.setIsVerified(false);
        newUser.setActive(false);
        User savedUser = userRepository.save(newUser);
        int ttl = otpService.generateAndSend(savedUser, OtpPurpose.REGISTER);
        if(ttl > 0){
            auditService.log(AuditEvent.REGISTER, savedUser.getId(), ipAddress, userAgent);
            auditService.log(AuditEvent.OTP_SENT, savedUser.getId(), ipAddress, userAgent,
                    "{\"purpose\":\"REGISTER\"}");
            return new OtpSentResponse(savedUser.getId(),"OTP sent",ttl);
        }
        else {
            ErrorResponse errorResponse = new ErrorResponse();
            errorResponse.setStatus(HttpStatus.BAD_GATEWAY.value());
            errorResponse.setTimestamp(LocalDateTime.now());
            errorResponse.setErrorDetails(new ErrorDetails("otp", "Failed to send otp"));
            return new OtpSentResponse(newUser.getId(), "Failed to send OTP", ttl, errorResponse);
        }
/*        GeoLocation geo = newUser.getGeoLocation();
        if(geo == null || geo.getCity() == null ||
                (geo.getLatitude() == null || geo.getLongitude() == null)){
            newUser.getGeoLocation().setGeoStatus(GeoStatus.PENDING);
        }
        else {
            newUser.setGeoLocation(geo);
            newUser.getGeoLocation().setGeoStatus(GeoStatus.COMPLETED);
        }

        String targetRole = registerRequest.getRole() != null && !registerRequest.getRole().isBlank()
                ? registerRequest.getRole().toUpperCase()
                : AppPermissions.ROLE_DONOR;

        if (AppPermissions.ROLE_SUPER_ADMIN.equals(targetRole)) {
            throw new IllegalArgumentException("Cannot self-register as SUPER_ADMIN");
        }

        Role role = roleRepository.findByName(targetRole).orElseThrow(
                ()-> new IllegalArgumentException("Role not found: "+targetRole));
        newUser.addRole(role);
        User savedUser = userRepository.save(newUser);
        return  savedUser;*/
    }

    @Override
    @Transactional
    public RestApiResponse<User> registerUser(RegisterRequest registrationRequest) {
        if (registrationRequest == null) {
            return Utils.buildErrorRestResponse(HttpStatus.BAD_REQUEST, "regRequest", "Registration request must not be null");
        }
        log.debug("Registering new user : name = {}", registrationRequest.getFirstName());

        /*boolean emailExists = userRepository.existsByEmail(registrationRequest.getEmail());
        boolean phoneExists = userRepository.existsByPhone(registrationRequest.getPhone());*/
        boolean emailExists = false,phoneExists = false;
            if (emailExists || phoneExists) {
                String message = emailExists && phoneExists ? "Email and Phone number already in use"
                                : emailExists ? "Email already in use"
                                : "Phone number already in use";
                log.info("Duplicate found : "+message);
                return Utils.buildErrorRestResponse(HttpStatus.CONFLICT, "email/password", message);
            }
            try {
                String encodedPassword = passwordEncoder.encode(registrationRequest.getPassword());
                User newUser = User.of(registrationRequest, encodedPassword, null, null);
                // update geolocation after otp verification of new flow
                GeoLocation geo = newUser.getGeoLocation();
                if(geo != null && geo.getCity() != null &&
                        (geo.getLatitude() == null || geo.getLongitude() == null)){
                    newUser.getGeoLocation().setGeoStatus(GeoStatus.PENDING);
                }
                else {
                    newUser.setGeoLocation(geo);
                    newUser.getGeoLocation().setGeoStatus(GeoStatus.COMPLETED);
                }

                User savedUser = userRepository.save(newUser);
                log.info("User saved to db: userId = {}, name = {}", savedUser.getId(), savedUser.getFirstName());

                UserRegisteredEvent event = UserRegisteredEvent.from(savedUser);
                publishAfterCommit(event);
                log.info("Published user-registered event for userId={}", savedUser.getId());
                return Utils.buildSuccessRestResponse(HttpStatus.CREATED,"Registration successful", savedUser);

            } catch (DataIntegrityViolationException e) {
                log.warn("Unique constraint violation during registration for email={}: {}",
                        registrationRequest.getEmail(), e.getMessage());
                return Utils.buildErrorRestResponse(HttpStatus.CONFLICT, "email/password", "Email or phone number already in use");
            } catch (Exception e) {
                log.error("Registration failed for username={}: {}", registrationRequest.getFirstName(), e.getMessage(), e);
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Utils.buildErrorRestResponse(HttpStatus.INTERNAL_SERVER_ERROR, "","Registration failed");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public User findByPhone(String phone) {
        return piiTokenizer.findByPlaintext(phone)
                .flatMap(token -> userRepository.findByPhoneToken(token))
                .orElseThrow(()->
                        new AuthException(ErrorCode.USER_NOT_FOUND,
                                "No account found for this phone number"));
    }

    @Override
    public boolean existByEmailToken(PiiToken emailToken) {
        return userRepository.existsByEmailToken(emailToken);
    }

    @Override
    public boolean existByPhoneToken(PiiToken phoneToken) {
        return userRepository.existsByPhoneToken(phoneToken);
    }

    @Override
    @Transactional
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User markVerified(User user) {
        user.markVerified();
        return saveUser(user);
    }

    @Override
    public User getUserWithRolePermissions(UUID userId) {
        log.debug("====== Came to get role permissions for userId : {} ==========", userId);
        User userWithRolePermissions = userRepository.findUserWithRolesAndPermissionsById(userId)
                .orElseThrow(() -> {
                    log.error("User not found while getting role permissions for userId: {}", userId);
                    return new UserNotFoundException("User not found: " + userId);
                });
        log.debug("Roles for userId: {} = {}", userId, userWithRolePermissions.getRoles());
        return userWithRolePermissions;
    }

    @Override
    @Transactional
    public User completeRegistrationAfterOtpVerification(User user) {
        if (user.getIsVerified()) {
            throw new AuthException(ErrorCode.ALREADY_VERIFIED,"User is already verified");
        }
        Role defaultRole = roleRepository.findByName(ROLE_DEFAULT)
                .orElseThrow(() ->
                        new IllegalStateException("Default role not configured: " + ROLE_DEFAULT));
        user.markVerified();
        user.resetFailedOtpAttempts();
        user.addRole(defaultRole);
        return user;
    }

    @Override
    public User completeLoginAfterOtpVerification(User user) {
        if (!user.getIsVerified()) {
            throw new AuthException(ErrorCode.USER_NOT_VERIFIED,"User account is not verified");
        }
        user.resetFailedOtpAttempts();
        return user;
    }

 /*   @Override
    public int registerFailedOtpAttempt(UUID userId, int maxAttempts, Instant lockedUntil) {
        return userRepository.registerFailedOtpAttempt(userId,maxAttempts,lockedUntil);
    }*/


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
