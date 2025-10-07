package com.bd.blooddonerfinder.model.es.documents;

import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.model.es.Location;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(createIndex = false, indexName = "donor_info_index")
public class UserDocument implements Serializable {

    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Keyword)
    private String email;

    @Field(type = FieldType.Keyword)
    private String phone;

    @Field(type = FieldType.Keyword)
    private String bloodGroup;

    @Field(type = FieldType.Keyword)
    private String role;

    @Field(type = FieldType.Boolean)
    private Boolean isVerified;

    @Field(type = FieldType.Boolean)
    private Boolean isAvailable;

    @Field(type = FieldType.Keyword)
    private String lastDonationDate;

    @Field(type = FieldType.Object)
    private Location location;

    @Field(type = FieldType.Keyword)
    private String createdAt;

    @Field(type = FieldType.Keyword)
    private String updatedAt;

    public static UserDocument from(User user){
        if(user == null) return null;
        UserDocument doc = UserDocument.builder()
                .id(user.getId().toString())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .bloodGroup(user.getBloodGroup().name())
                .role(user.getRole().name())
                .isVerified(user.getIsVerified())
                .isAvailable(user.getIsAvailable())
                .lastDonationDate(user.getLastDonationDate().toString())
                .createdAt(user.getCreatedAt().toString())
                .build();
        if(user.getGeoLocation() != null){
            Location location = Location.builder()
                    .address(user.getGeoLocation().getAddress())
                    .city(user.getGeoLocation().getCity())
                    .district(user.getGeoLocation().getDistrict())
                    .latitude(user.getGeoLocation().getLatitude())
                    .longitude(user.getGeoLocation().getLongitude())
                    .zipcode(user.getGeoLocation().getZipcode())
                    .build();
            doc.setLocation(location);
        }
        return doc;
    }
}
