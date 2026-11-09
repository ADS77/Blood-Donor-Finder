package com.bd.blooddonerfinder.util;

import com.bd.blooddonerfinder.payload.response.ErrorDetails;
import com.bd.blooddonerfinder.payload.response.RestApiResponse;
import com.bd.blooddonerfinder.payload.response.SuccessDetails;
import org.springframework.http.HttpStatus;

public class Utils {
    public static <T> RestApiResponse<T> buildSuccessRestResponse(HttpStatus httpStatus, T klass) {
        return new RestApiResponse(httpStatus, new SuccessDetails(klass));
    }

    public static <T> RestApiResponse<T> buildErrorRestResponse(HttpStatus httpStatus, String filed, String message) {
        return filed != null ? new RestApiResponse(httpStatus, new ErrorDetails(filed, message)) : new RestApiResponse(httpStatus, new ErrorDetails(message));
    }
}
