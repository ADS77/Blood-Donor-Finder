package com.bd.blooddonorfinder.model.es;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Address block embedded in donor search documents.
 *
 * Jackson tolerance notes: donor documents in Elasticsearch store coordinates in
 * geo_point form ({"lat":..,"lon":..}, see RegisteredDonorDocument) while this
 * model is also built programmatically from JPA entities with
 * latitude/longitude. The aliases plus ignoreUnknown let the same model accept
 * both shapes when search hits are mapped back from the index.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Location implements Serializable {
    @Field(type = FieldType.Keyword)
    private String address;
    @Field(type = FieldType.Keyword)
    private String city;
    @Field(type = FieldType.Keyword)
    private String district;
    @JsonAlias({"lat"})
    private double latitude;
    @JsonAlias({"lon"})
    private double longitude;
    @Field(type = FieldType.Keyword)
    private String zipcode;
}
