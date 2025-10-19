package com.bd.blooddonerfinder.model.es.QueryModel;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.bd.blooddonerfinder.model.es.common.SearchCriteria;
import com.bd.blooddonerfinder.util.ElasticQueryUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
@Data
@Getter
@Setter
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
    public static Query userQueryModelBuilder(UserQueryModel queryModel) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        if (queryModel.getId() != null) {
            boolBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getId(), "id", false));
        }
        if (queryModel.getName() != null) {
            boolBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getName(), "name", true));
        }
        if (queryModel.getPhone() != null) {
            boolBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getPhone(), "phone", false));
        }
        if (queryModel.getBloodGroup() != null) {
            boolBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getBloodGroup(), "bloodGroup", false));
        }
        if (queryModel.getEmail() != null) {
            boolBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getEmail(), "email", true));
        }
        if (queryModel.getRole() != null) {
            boolBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getRole(), "role", true));
        }
        if (queryModel.getIsVerified() != null) {
            boolBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getIsVerified(), "isVerified", false));
        }
        if (queryModel.getIsAvailable() != null) {
            boolBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getIsAvailable(), "isAvailable", false));
        }
        if (queryModel.getLastDonationDate() != null) {
            boolBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getLastDonationDate(), "lastDonationDate", false));
        }
        if (queryModel.getCreatedAt() != null) {
            boolBuilder.must(ElasticQueryUtils.getFieldQueryBuilder(queryModel.getCreatedAt(), "createdAt", false));
        }

        // ✅ Handle nested location query
        if (queryModel.getLocationQueryModel() != null) {
            Query locationQuery = LocationQueryModel.LocationQueryModelBuilder("location.", queryModel.getLocationQueryModel());
            boolBuilder.must(locationQuery);
        }

        return new Query.Builder().bool(boolBuilder.build()).build();
    }

}
