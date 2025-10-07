package com.bd.blooddonerfinder.model.common;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
public class SearchCriteria implements Serializable {
    private List<String>values;
    private List<String>patterns;
    private List<SearchRange> ranges;
    private SearchType searchType;
    private String fieldId;
    public SearchCriteria(List<String> values, SearchType searchType){
        this.values = values;
        this.searchType = searchType;
    }

    public SearchCriteria(String value, SearchType searchType){
        this.values = Collections.singletonList(value);
        this.searchType = searchType;
    }

    public SearchCriteria(SearchRange range, SearchType searchType){
        this.ranges = Collections.singletonList(range);
        this.searchType = searchType;
    }
}
