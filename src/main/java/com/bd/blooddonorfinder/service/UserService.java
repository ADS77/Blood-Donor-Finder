package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.model.PiiToken;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.payload.request.RegisterRequest;
import com.bd.blooddonorfinder.payload.response.OtpSentResponse;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import org.apache.kafka.common.protocol.types.Field;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserService {

    User findById(UUID userId);
    OtpSentResponse registerNewUser(RegisterRequest registerRequest, String ipAddress, String userAgent);

    RestApiResponse<User> registerUser(RegisterRequest registrationRequest);

    public User findByPhone(String phone);

    public boolean existByEmailToken(PiiToken emailToken);

    boolean existByPhoneToken(PiiToken phoneToken);

    User saveUser(User user);

    User markVerified(User user);

    User getUserWithRolePermissions(UUID userId);

    User completeRegistrationAfterOtpVerification(User user);

    User completeLoginAfterOtpVerification(User user);

    //int registerFailedOtpAttempt(UUID userId, int maxAttempts, Instant lockedUntil);
}
