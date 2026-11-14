package com.bd.blooddonorfinder.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ListResponse<T> implements Serializable {
    private List<T> data;
    private long count;
    private long time;
    private List<String>errorFields;
    /** ES {@code search_after} cursor for the next page, or null on the last page. */
    private Object[] searchAfter;

}
