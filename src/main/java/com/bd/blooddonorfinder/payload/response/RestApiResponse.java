package com.bd.blooddonorfinder.payload.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import java.util.List;

@Data
@NoArgsConstructor
public class RestApiResponse<T>  {
    private T data;
    private List<T> listData;
    private SuccessDetails<T> success;
    private HttpStatus status;
    private StatusCode statusCode;
    private String message;
    private int totalCount;
    private ErrorDetails error;

    public static <T> RestApiResponse<T> of (T data){
        RestApiResponse response = new RestApiResponse();
        response.getSuccess().setData(data);
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
        response.setData(data);
        response.setStatus(HttpStatus.OK);
        response.setMessage(message);
        response.totalCount = 1;
        return response;
    }

    public static <T> RestApiResponse<T> success(List<T> listData, String message) {
        RestApiResponse<T> response = new RestApiResponse<>();
        response.setListData(listData);
        response.setStatus(HttpStatus.OK);
        response.setMessage(message);
        response.totalCount = listData.size();
        return response;
    }

    public static <T> RestApiResponse<T> success(int totalCount, String message, HttpStatus status){
        RestApiResponse<T> response = new RestApiResponse<>();
        response.totalCount = totalCount;
        response.message = message;
        response.status = status;
        return response;
    }

}
