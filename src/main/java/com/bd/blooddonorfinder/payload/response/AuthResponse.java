package com.bd.blooddonorfinder.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.io.Serializable;
@Data
@Builder
@NoArgsConstructor
@Getter
public class AuthResponse implements Serializable {
    private String username;
    @JsonProperty("access_token")
    private String accessToken;
    @JsonProperty("refresh_token")
    private String refreshToken;

    public AuthResponse(String username, String accessToken, String refreshToken){
        this.username = username;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

}
