package com.bd.blooddonorfinder.model.es.documents;

import com.bd.blooddonorfinder.kafka.model.events.UserRegisteredEvent;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.*;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

import java.time.LocalDateTime;

@Document(indexName = "registered_donors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisteredDonorDocument {
    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Keyword)
    private String email;

    @Field(type = FieldType.Keyword)
    private String phoneNumber;

    @Field(type = FieldType.Keyword)
    private String role;

    @Field(type = FieldType.Keyword)
    private String bloodGroup;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String city;

    @Field(type = FieldType.Keyword)
    private String district;

    @Field(type = FieldType.Keyword)
    private String country;

    @Field(type = FieldType.Keyword)
    private String zipcode;

    @GeoPointField
    private GeoPoint location;

    @Field(type = FieldType.Boolean)
    private Boolean isVerified;

    @Field(type = FieldType.Keyword)
    private String imgUrl;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime lastDonationDate;

    @Field(type = FieldType.Long)
    private Long totalDonations;

    @Field(type = FieldType.Double)
    private Double rating;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime updatedAt;

    @Field(type = FieldType.Long)
    private Long version;

    public static RegisteredDonorDocument from (UserRegisteredEvent event){
        return  RegisteredDonorDocument.builder()
                .id(event.getUserId().toString())
                .name(event.getName())
                .email(event.getEmail())
                .phoneNumber(event.getPhone())
                .bloodGroup(event.getBloodGroup())
                .city(event.getCity())
                .district(event.getDistrict())
                .country(event.getCountry())
                .location(new GeoPoint(event.getLatitude().doubleValue(), event.getLongitude().doubleValue()))
                .isVerified(event.getIsVerified())
                .imgUrl(event.getImgUrl())
                .lastDonationDate(event.getLastDonationDate())
                .rating(event.getRating())
                .totalDonations(event.getTotalDonations())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }

}
