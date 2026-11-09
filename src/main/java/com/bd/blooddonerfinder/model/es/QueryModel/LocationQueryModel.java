package com.bd.blooddonerfinder.model.es.QueryModel;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.bd.blooddonerfinder.model.es.common.SearchCriteria;
import com.bd.blooddonerfinder.util.ElasticQueryUtils;
import lombok.Data;
import lombok.Getter;

import java.io.Serializable;

@Data
@Getter
public class LocationQueryModel implements Serializable {
    private SearchCriteria address;
    private SearchCriteria city;
    private SearchCriteria district;
    private SearchCriteria latitude;
    private SearchCriteria longitude;
    private SearchCriteria zipcode;

    public LocationQueryModel(){
    }

    public static Query LocationQueryModelBuilder(String prefix,LocationQueryModel queryModel){
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
        if(queryModel.getAddress() != null){
            boolQueryBuilder.must( ElasticQueryUtils.getFieldQueryBuilder(queryModel.getAddress(), prefix.concat("address"), false));
        }
        if(queryModel.getCity() != null){
            boolQueryBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getCity(), prefix.concat("city"), false));
        }
        if(queryModel.getDistrict() != null){
            boolQueryBuilder.must( ElasticQueryUtils.getFieldQueryBuilder(queryModel.getDistrict(), prefix.concat("district"), false));
        }
        if(queryModel.getLatitude() != null){
            boolQueryBuilder.must( ElasticQueryUtils.getFieldQueryBuilder(queryModel.getLatitude(), prefix.concat("latitude"), false));
        }
        if(queryModel.getLongitude() != null){
            boolQueryBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getLongitude(), prefix.concat("longitude"), false));
        }
        if(queryModel.getZipcode() != null){
            boolQueryBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getZipcode(), prefix.concat("zipcode"), false));
        }
        return boolQueryBuilder.build()._toQuery();
    }
}
