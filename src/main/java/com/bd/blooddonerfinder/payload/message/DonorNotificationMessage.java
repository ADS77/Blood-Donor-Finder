package com.bd.blooddonerfinder.payload.message;

import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.payload.request.DonorSearchRequest;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;
@EqualsAndHashCode
@ToString
@Getter
public class DonorNotificationMessage implements Serializable {
    private static final Long serialVersionUid = 1L;
    private DonorSearchRequest searchRequest;
    private List<User> eligibleDonors;
    private Long timestamp;
    private String notificationId;

    public DonorNotificationMessage(DonorSearchRequest searchRequest, List<User> eligibleDonors){
        this.searchRequest = searchRequest;
        this.eligibleDonors = eligibleDonors;
        this.timestamp = System.currentTimeMillis();
        this.notificationId= generateNotificationId();
    }

    private String generateNotificationId() {
        String bloodGroupCode = searchRequest.getBloodGroup() != null ?
                searchRequest.getBloodGroup().getDisplayName() : "UNK";

        return bloodGroupCode + "_" +
                searchRequest.getRadius() + "_" +
                System.currentTimeMillis();
    }

    public int getDonorCount(){
        return eligibleDonors!= null ? eligibleDonors.size() : 0;
    }

}
