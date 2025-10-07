package com.bd.blooddonerfinder.model.es.QueryModel;

import com.bd.blooddonerfinder.model.common.SearchCriteria;
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
    private LocationModel locationModel;
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

    public void setLocationModel(LocationModel locationModel) {
        this.locationModel = locationModel;
    }

    public void setCreatedAt(SearchCriteria createdAt) {
        this.createdAt = createdAt;
    }

    @JsonIgnore
    public static void userQueryModelBuilder(BoolQueryBuilder rootQueryBuilder, UserQueryModel queryModel){

    }


}
