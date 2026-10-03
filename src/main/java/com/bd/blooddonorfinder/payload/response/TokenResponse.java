package com.bd.blooddonorfinder.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenResponse {
    @JsonProperty("access_token")
    private String accessToken;
    @JsonProperty("refresh_token")
    private String refreshToken;
    @JsonProperty("token_type")
    private String tokenType = "Bearer";
    @JsonProperty("otp_expires_in_seconds")
    private long expiresIn;
    private String username;
    private UUID userId;
    private List<String> authorities;
    public static TokenResponse of(String accessToken, String refreshToken,
                                   long expiresInSeconds, String username, List<String> roles) {
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresInSeconds)
                .username(username)
                .authorities(roles)
                .build();
    }
}