package com.bd.blooddonerfinder.service;

import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.payload.request.UserRegistrationRequest;
import com.bd.blooddonerfinder.payload.response.RestApiResponse;

public interface UserService {
    RestApiResponse<User> registerUser(UserRegistrationRequest registrationRequest);
}
