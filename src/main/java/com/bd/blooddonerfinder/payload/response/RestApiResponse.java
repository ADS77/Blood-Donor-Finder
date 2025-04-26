package com.bd.blooddonerfinder.payload.response;

import lombok.Data;
import org.springframework.http.HttpStatus;

import java.util.List;

@Data
public class RestApiResponse<T>  {
    private T data;
    private List<T> listData;
    private HttpStatus status;
    private String message;

    public static <T> RestApiResponse<T> success(T data, String message) {
        RestApiResponse<T> response = new RestApiResponse<>();
        response.setData(data);
        response.setStatus(HttpStatus.OK);
        response.setMessage(message);
        return response;
    }

    public static <T> RestApiResponse<T> success(List<T> listData, String message) {
        RestApiResponse<T> response = new RestApiResponse<>();
        response.setListData(listData);
        response.setStatus(HttpStatus.OK);
        response.setMessage(message);
        return response;
    }

}
