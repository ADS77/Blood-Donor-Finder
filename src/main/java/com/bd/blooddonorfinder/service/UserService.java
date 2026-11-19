package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.model.PiiToken;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.payload.enums.LoginIdentifierType;
import com.bd.blooddonorfinder.payload.request.RegisterRequest;
import com.bd.blooddonorfinder.payload.response.DonorDetailDto;
import com.bd.blooddonorfinder.payload.response.OtpSentResponse;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.payload.response.UserProfileDto;
import org.apache.kafka.common.protocol.types.Field;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserService {

    User findById(UUID userId);

    /**
     * Full profile of one user, including their own contact details. Callers
     * must only pass the authenticated principal's id.
     */
    UserProfileDto getProfile(UUID userId);

    /**
     * Public donor view: no contact details and neighbourhood-level location.
     * Unknown, inactive or non-donor accounts read as not found.
     */
    DonorDetailDto getDonorDetail(UUID donorId);

    /**
     * Non-throwing variant of {@link #findById(UUID)} for callers that must
     * handle "absent" themselves inside their own transaction — the throwing
     * version marks the caller's transaction rollback-only when caught.
     */
    Optional<User> findOptionalById(UUID userId);

    OtpSentResponse registerNewUser(RegisterRequest registerRequest, String ipAddress, String userAgent);

    RestApiResponse<User> registerUser(RegisterRequest registrationRequest);

    public User findByPhone(String phone);

    User findByIdentifier(String identifier, LoginIdentifierType identifierType);

    public boolean existByEmailToken(PiiToken emailToken);

    boolean existByPhoneToken(PiiToken phoneToken);

    User saveUser(User user);

    User markVerified(User user);

    User getUserWithRolePermissions(UUID userId);

    User completeRegistrationAfterOtpVerification(User user);

    User completeLoginAfterOtpVerification(User user);

    //int registerFailedOtpAttempt(UUID userId, int maxAttempts, Instant lockedUntil);
}
