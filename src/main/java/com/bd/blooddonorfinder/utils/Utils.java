package com.bd.blooddonorfinder.utils;

import com.bd.blooddonorfinder.payload.response.ErrorDetails;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.payload.response.SuccessDetails;
import org.springframework.http.HttpStatus;

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
}
