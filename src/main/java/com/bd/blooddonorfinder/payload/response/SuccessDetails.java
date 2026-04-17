package com.bd.blooddonorfinder.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class SuccessDetails<T> implements Serializable {
    private T data;
    private String message;
    private String template;
    private String redirect;
     public SuccessDetails(T data){
         this.data = data;
     }

    public SuccessDetails(T data, String message) {
        this.data = data;
        this.message = message;
    }
}
