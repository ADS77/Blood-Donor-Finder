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
    private int totalCount;

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
