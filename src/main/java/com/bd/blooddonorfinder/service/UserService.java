package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.model.User;
import com.bd.blooddonorfinder.payload.request.UserRegistrationRequest;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;

public interface UserService {
    RestApiResponse<User> registerUser(UserRegistrationRequest registrationRequest);
}
