package com.bd.blooddonorfinder.service.auth;

import com.bd.blooddonorfinder.model.User;
import com.bd.blooddonorfinder.payload.request.AuthRequest;
import com.bd.blooddonorfinder.payload.response.AuthResponse;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

public interface AuthService {
    RestApiResponse<AuthResponse> login(AuthRequest authRequest);
    List<String> getUserRole(String token);

    User getUserDetails(String token);

    RestApiResponse<String> logout(HttpServletRequest request, HttpServletResponse response,
                                   String token, String tokenType);
}
