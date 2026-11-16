package com.bd.blooddonorfinder.payload.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.util.List;

@Data
@NoArgsConstructor
public class RestApiResponse<T>  {
    private SuccessDetails<T> success;
    private HttpStatus status;
    private StatusCode statusCode;
    private String message;
    private ErrorDetails error;

    public static <T> RestApiResponse<T> of (T data){
        RestApiResponse<T> response = new RestApiResponse<>();
        response.setSuccess(new SuccessDetails<>(data));
        response.setStatus(HttpStatus.OK);
        return response;
    }

    public RestApiResponse(HttpStatus httpStatus, SuccessDetails successDetails) {
        this.statusCode = StatusCode.SUCCESS;
        this.status = httpStatus;
        this.success = successDetails;
    }

    public RestApiResponse(HttpStatus status, ErrorDetails error) {
        this.statusCode = StatusCode.ERROR;
        this.status = status;
        this.error = error;
    }

    public static <T> RestApiResponse<T> success(T data, String message) {
        RestApiResponse<T> response = new RestApiResponse<>();
        // A list payload must populate the list slot so totalCount reflects the
        // page size (see SuccessDetails list constructor); a bare object gets
        // totalCount=1. Every success path also carries statusCode=SUCCESS so
        // clients can branch on it consistently across endpoints.
        response.setSuccess(new SuccessDetails<>(data, message));
        response.setStatus(HttpStatus.OK);
        response.setStatusCode(StatusCode.SUCCESS);
        response.setMessage(message);
        return response;
    }

    public static <T> RestApiResponse<T> success(List<T> listData, String message) {
        RestApiResponse<T> response = new RestApiResponse<>();
        SuccessDetails<T> successDetails = new SuccessDetails<>(listData,message);
        response.setSuccess(successDetails);
        response.setStatus(HttpStatus.OK);
        response.setStatusCode(StatusCode.SUCCESS);
        response.setMessage(message);
        return response;
    }

}
