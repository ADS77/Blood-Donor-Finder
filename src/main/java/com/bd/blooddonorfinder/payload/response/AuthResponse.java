package com.bd.blooddonorfinder.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String username;
    @JsonProperty("token_response")
    private TokenResponse tokenResponse;
    @JsonProperty("otp_sent_response")
    private OtpSentResponse otpSentResponse;

    public static AuthResponse of (String username){
        return buildAuthResponse(
                username,
                null,
                null);
    }

    private static AuthResponse buildAuthResponse(String username, TokenResponse tokenResponse, OtpSentResponse otpSentResponse){
        return AuthResponse.builder()
                .username(username)
                .otpSentResponse(otpSentResponse)
                .tokenResponse(tokenResponse)
                .build();
    }
}
