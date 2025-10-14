package com.bd.blooddonerfinder.model.es.QueryModel;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.bd.blooddonerfinder.model.common.SearchCriteria;
import com.bd.blooddonerfinder.util.ElasticQueryUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.Getter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
@Data
@Getter
public class UserQueryModel implements Serializable {
    private SearchCriteria id;
    private SearchCriteria name;
    private SearchCriteria phone;
    private SearchCriteria bloodGroup;
    private SearchCriteria email;
    private SearchCriteria role;
    private SearchCriteria isVerified;
    private SearchCriteria isAvailable;
    private SearchCriteria lastDonationDate;
    private LocationQueryModel locationQueryModel;
    private SearchCriteria createdAt;
    private List<String> errorList;
    public UserQueryModel(){
        this.errorList = new ArrayList<>();
    }
    @JsonIgnore
    public List<String> getErrorFields() {
        return errorList;
    }

    public void setId(SearchCriteria id) {
        this.id = id;
    }

    public void setName(SearchCriteria name) {
        this.name = name;
    }

    public void setPhone(SearchCriteria phone) {
        this.phone = phone;
    }

    public void setBloodGroup(SearchCriteria bloodGroup) {
        this.bloodGroup = bloodGroup;
    }

    public void setEmail(SearchCriteria email) {
        this.email = email;
    }

    public void setRole(SearchCriteria role) {
        this.role = role;
    }

    public void setIsVerified(SearchCriteria isVerified) {
        this.isVerified = isVerified;
    }

    public void setIsAvailable(SearchCriteria isAvailable) {
        this.isAvailable = isAvailable;
    }

    public void setLastDonationDate(SearchCriteria lastDonationDate) {
        this.lastDonationDate = lastDonationDate;
    }

    public void setLocationQueryModel(LocationQueryModel locationQueryModel) {
        this.locationQueryModel = locationQueryModel;
    }

    public void setCreatedAt(SearchCriteria createdAt) {
        this.createdAt = createdAt;
    }

    @JsonIgnore
    public static Query userQueryModelBuilder(UserQueryModel queryModel){
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
        if(queryModel.getId() != null){
            boolQueryBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getId(),"id", false));
        }
        if(queryModel.getName() != null){
            boolQueryBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getName(),"name", false));
        }
        if(queryModel.getPhone() != null){
            boolQueryBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getPhone(),"phone", false));
        }
        if(queryModel.getBloodGroup() != null){
            boolQueryBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getBloodGroup(),"bloodGroup", false));
        }
        if(queryModel.getEmail() != null){
            boolQueryBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getEmail(),"email", true));
        }
        if(queryModel.getIsAvailable() != null){
            boolQueryBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getIsAvailable(),"isAvailable", false));
        }
        if(queryModel.getIsVerified() != null){
            boolQueryBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getIsVerified(),"isVerified", false));
        }
        if(queryModel.getLastDonationDate() != null){
            boolQueryBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getLastDonationDate(),"lastDonationDate", false));
        }
        if(queryModel.getLocationQueryModel() != null){
            LocationQueryModel.LocationQueryModelBuilder("location".concat("."), queryModel.locationQueryModel);
        }
        return  boolQueryBuilder.build()._toQuery();
    }


}
