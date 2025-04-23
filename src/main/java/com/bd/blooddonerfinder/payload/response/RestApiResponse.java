package com.bd.blooddonerfinder.payload.response;

import lombok.Data;
import org.springframework.http.HttpStatus;
@Data
public class RestApiResponse<T>  {
    private T data;
    private HttpStatus status;
    private String message;

//    public static <T> RestApiResponse<T> success(T data, String message) {
//        RestApiResponse<T> response = new RestApiResponse<>();
//        response.setData(data);
//        response.setStatus(HttpStatus.OK);
//        response.setMessage(message);
//        return response;
//    }

}
