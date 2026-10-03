package com.bd.blooddonorfinder.service.auth;

import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.payload.request.AuthRequest;
import com.bd.blooddonorfinder.payload.request.RegisterRequest;
import com.bd.blooddonorfinder.payload.request.VerifyOtpRequest;
import com.bd.blooddonorfinder.payload.response.AuthResponse;
import com.bd.blooddonorfinder.payload.response.OtpSentResponse;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.payload.response.TokenResponse;
import com.bd.blooddonorfinder.security.context.SondhanClientContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

public interface AuthService {
    RestApiResponse<AuthResponse> login(AuthRequest authRequest);
    RestApiResponse<OtpSentResponse> register(RegisterRequest registerRequest, SondhanClientContext clientContext);
    List<String> getUserRole(String token);

    User getUserDetails(String token);

    RestApiResponse<String> logout(HttpServletRequest request, HttpServletResponse response,
                                   String token, String tokenType);

    RestApiResponse<AuthResponse> verifyOtp(SondhanClientContext clientContext, VerifyOtpRequest verifyOtpRequest);
}
