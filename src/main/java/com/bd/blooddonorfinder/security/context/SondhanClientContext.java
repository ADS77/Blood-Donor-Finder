package com.bd.blooddonorfinder.security.context;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class SondhanClientContext {
    private String ipAddress;
    private String userAgent;
    private String requestId;
    private String deviceId;
}
