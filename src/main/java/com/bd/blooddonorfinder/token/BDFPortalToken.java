package com.bd.blooddonorfinder.token;

import lombok.Data;

@Data
public class BDFPortalToken {
    private String accessToken;
    private String refreshToken;
}
