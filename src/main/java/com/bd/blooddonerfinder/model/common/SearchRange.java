package com.bd.blooddonerfinder.model.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchRange implements Serializable {
    private String min;
    private String max;

}
