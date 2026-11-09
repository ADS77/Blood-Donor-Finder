package com.bd.blooddonorfinder.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class SuccessDetails<T> implements Serializable {
    private T data;
    private List<T> listData;
    private String message;
    private String template;
    private String redirect;
    private int totalCount;

    public SuccessDetails(T data){
         this.data = data;
         this.totalCount = 1;
     }
    public SuccessDetails(List<T> listData){
        this.listData = listData;
        this.totalCount = listData.size();
    }

    public SuccessDetails(T data, String message) {
        this.data = data;
        this.message = message;
    }
    public SuccessDetails(List<T> listData, String message) {
        this.listData = listData;
        this.totalCount = listData.size();
        this.message = message;
    }
}
