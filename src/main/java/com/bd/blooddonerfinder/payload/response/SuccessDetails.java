package com.bd.blooddonerfinder.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

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

}
