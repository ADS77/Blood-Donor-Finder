package com.bd.blooddonorfinder.kafka.model.events;

import com.bd.blooddonorfinder.kafka.interfaces.IndexAbleEvent;
import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import com.bd.blooddonorfinder.model.User;
import com.bd.blooddonorfinder.utils.constants.ElasticIndexes;
import com.bd.blooddonorfinder.utils.constants.KafkaTopics;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class UserRegisteredEvent extends BaseEvent implements IndexAbleEvent {
    private Long userId;
    private String username;
    private String email;
    private String phone;
    private String role;
    private String bloodGroup;
    private String address;
    private String city;
    private String district;
    private String country;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String zipcode;
    private Boolean isVerified;
    private String imgUrl;
    private LocalDateTime lastDonationDate;
    private Long totalDonations;
    private Double rating;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public UserRegisteredEvent(String eventId){
        super(eventId,KafkaTopics.USER_REGISTERED,"UserRegisteredEvent");
    }

    public static UserRegisteredEvent from(User user) {
        UserRegisteredEvent event = new UserRegisteredEvent(UUID.randomUUID().toString());
        event.setUserId(user.getId());
        event.setUsername(user.getName());
        event.setEmail(user.getEmail());
        event.setPhone(user.getPhone());
        //event.setRole(user.getRole().name()!= null ? user.getRole().name() : "reg_user");
        event.setBloodGroup(user.getBloodGroup().name());
        event.setIsVerified(user.getIsVerified());
        event.setImgUrl(user.getImageUrl());
        event.setLastDonationDate(user.getLastDonationDate());
        event.setTotalDonations(user.getTotalDonations() == null ? 0L : user.getTotalDonations());
        event.setRating(user.getRating() == null ? 0.0 : user.getRating());
        event.setCreatedAt(user.getCreatedAt());
        event.setUpdatedAt(user.getUpdatedAt());
        if(user.getGeoLocation() != null){
            event.setAddress(user.getGeoLocation().getAddress());
            event.setDistrict(user.getGeoLocation().getDistrict());
            event.setCity(user.getGeoLocation().getCity());
            if( user.getGeoLocation().getCountry() != null){
                event.setCountry(user.getGeoLocation().getCountry());
            }
            if(user.getGeoLocation().getLatitude() != null){
                event.setLatitude(user.getGeoLocation().getLatitude());
            }
            if(user.getGeoLocation().getLongitude() != null){
                event.setLongitude(user.getGeoLocation().getLongitude());
            }
            if(user.getGeoLocation().getZipcode() !=null){
                event.setZipcode(user.getGeoLocation().getZipcode());
            }
        }
        event.setAggregateId(String.valueOf(user.getId()));
        event.setVersion(user.getVersion());
        return event;
    }


    @Override
    public String getIndexName() {
        return ElasticIndexes.REGISTERED_DONORS_INDEX;
    }

}
