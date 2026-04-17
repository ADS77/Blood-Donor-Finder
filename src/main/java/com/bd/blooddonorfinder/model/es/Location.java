package com.bd.blooddonorfinder.model.es;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Location implements Serializable {
    @Field(type = FieldType.Keyword)
    private String address;
    @Field(type = FieldType.Keyword)
    private String city;
    @Field(type = FieldType.Keyword)
    private String district;
    private BigDecimal latitude;
    private BigDecimal longitude;
    @Field(type = FieldType.Keyword)
    private String zipcode;
}
