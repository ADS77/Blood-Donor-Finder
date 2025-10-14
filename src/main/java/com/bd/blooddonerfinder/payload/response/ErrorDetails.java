package com.bd.blooddonerfinder.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class ErrorDetails implements Serializable {
    private String field;
    private String message;
    public ErrorDetails(String message){
        this.message = message;
    }
    public ErrorDetails(String field, String message){
        this.field = field;
        this.message = message;
    }
}
