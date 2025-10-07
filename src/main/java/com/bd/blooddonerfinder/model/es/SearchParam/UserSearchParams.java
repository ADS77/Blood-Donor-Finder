package com.bd.blooddonerfinder.model.es.SearchParam;

import com.bd.blooddonerfinder.model.es.QueryModel.UserQueryModel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
@Data
public class UserSearchParams implements Serializable {
    private UserQueryModel queryModel;
    private Integer start;
    private Integer length;
    private Object[] searchAfter;

    @JsonIgnore
    public int getPage(){
        if(length == null || length == 0){
            throw new IllegalArgumentException("Page length can't be 0");
        }
        if(start  == null || start < 0){
            start = 0;
        }
        return start/length;
    }

}
