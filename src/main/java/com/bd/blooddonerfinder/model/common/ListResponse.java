package com.bd.blooddonerfinder.model.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class ListResponse<T> implements Serializable {
    private List<T> data;
    private long count;
    private long time;
    private List<String>errorFields;

}
