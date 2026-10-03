package com.bd.blooddonorfinder.utils;

import com.bd.blooddonorfinder.exception.AuthException;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.enums.OtpPurpose;
import com.bd.blooddonorfinder.payload.response.ErrorDetails;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.payload.response.SuccessDetails;
import com.bd.blooddonorfinder.security.context.SondhanClientContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

public class Utils {
    public static <T> RestApiResponse<T> buildSuccessRestResponse(HttpStatus httpStatus, T klass) {
        return new RestApiResponse(httpStatus, new SuccessDetails(klass));
    }

    public static <T> RestApiResponse<T> buildSuccessRestResponse(HttpStatus httpStatus,String message, T klass) {
        return new RestApiResponse(httpStatus, new SuccessDetails(klass, message));
    }

    public static <T> RestApiResponse<T> buildErrorRestResponse(HttpStatus httpStatus, String filed, String message) {
        return filed != null ? new RestApiResponse(httpStatus, new ErrorDetails(filed, message)) : new RestApiResponse(httpStatus, new ErrorDetails(message));
    }

    public static int getHash(String str, int mod) {
        int r = 0;
        for (int i = 0; i < str.length(); i++){
            r = (r * 100003 + str.codePointAt(i)) % mod;
        }
        return r;
    }


    public static SondhanClientContext buildClientContext(HttpServletRequest httpRequest) {
        String ua = httpRequest.getHeader("User-Agent");
        return SondhanClientContext.builder()
                .ipAddress(httpRequest.getRemoteAddr())
                .userAgent(ua)
                .deviceId(buildDeviceId(ua))
                .build();
    }
    private static String buildDeviceId(String ua) {
        return StringUtils.hasText(ua)
                ? HashUtils.sha256Hex(ua).substring(0, 16)
                : "unknown";
    }

    public static OtpPurpose parseOtpPurpose(String purposeStr) {
        try {
            return OtpPurpose.valueOf(purposeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AuthException(ErrorCode.INVALID_REQUEST,
                    "Invalid OTP purpose: " + purposeStr);
        }
    }
}
