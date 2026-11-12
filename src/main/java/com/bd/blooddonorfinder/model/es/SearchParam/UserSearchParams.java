package com.bd.blooddonorfinder.model.es.SearchParam;

import com.bd.blooddonorfinder.model.es.QueryModel.UserQueryModel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class UserSearchParams implements Serializable {

    @NotNull(message = "queryModel is required")
    private UserQueryModel queryModel;

    @Min(value = 0, message = "start must be zero or greater")
    private Integer start;

    @Min(value = 1, message = "length must be at least 1")
    @Max(value = 100, message = "length must not exceed 100")
    private Integer length;

    private Object[] searchAfter;

    @JsonIgnore
    public int getPage() {
        if (length == null || length == 0) {
            throw new IllegalArgumentException("Page length can't be 0");
        }
        if (start == null || start < 0) {
            start = 0;
        }
        return start / length;
    }
}
