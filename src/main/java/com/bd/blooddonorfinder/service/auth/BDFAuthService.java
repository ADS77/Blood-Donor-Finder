package com.bd.blooddonorfinder.service.auth;

import com.bd.blooddonorfinder.model.User;
import com.bd.blooddonorfinder.payload.request.AuthRequest;
import com.bd.blooddonorfinder.payload.response.AuthResponse;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BDFAuthService implements AuthService{
    @Override
    public RestApiResponse<AuthResponse> login(AuthRequest authRequest) {
        return null;
    }

    @Override
    public List<String> getUserRole(String token) {
        return null;
    }

    @Override
    public User getUserDetails(String token) {
        return null;
    }

    @Override
    public RestApiResponse<String> logout(HttpServletRequest request, HttpServletResponse response, String token, String tokenType) {
        return null;
    }
}
