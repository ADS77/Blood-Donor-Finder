package com.bd.blooddonorfinder.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthDetails implements Serializable {
    private String username;
    private String accessToken;
    private String refreshToken;

}
