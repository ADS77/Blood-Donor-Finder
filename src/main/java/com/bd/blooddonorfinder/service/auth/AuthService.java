package com.bd.blooddonorfinder.service.auth;

import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.payload.request.LoginRequest;
import com.bd.blooddonorfinder.payload.request.RegisterRequest;
import com.bd.blooddonorfinder.payload.request.ResendOtpRequest;
import com.bd.blooddonorfinder.payload.request.VerifyOtpRequest;
import com.bd.blooddonorfinder.payload.response.AuthResponse;
import com.bd.blooddonorfinder.payload.response.OtpSentResponse;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.payload.response.TokenResponse;
import com.bd.blooddonorfinder.security.context.SondhanClientContext;
import com.bd.blooddonorfinder.security.exception.InvalidJwtTokenException;

import java.util.List;

public interface AuthService {

    RestApiResponse<AuthResponse> login(LoginRequest loginRequest, SondhanClientContext clientContext);

    RestApiResponse<OtpSentResponse> register(RegisterRequest registerRequest, SondhanClientContext clientContext);

    RestApiResponse<AuthResponse> verifyOtp(SondhanClientContext clientContext, VerifyOtpRequest verifyOtpRequest);

    RestApiResponse<OtpSentResponse> resendOtp(SondhanClientContext clientContext, ResendOtpRequest resendOtpRequest);

    TokenResponse refresh(String refreshToken, SondhanClientContext clientContext) throws InvalidJwtTokenException;

    void logout(String accessToken, String refreshToken, SondhanClientContext clientContext);

    List<String> getUserRole(String token);

    User getUserDetails(String token);
}
