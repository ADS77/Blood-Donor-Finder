package com.bd.blooddonorfinder.kafka.model.events;

import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import com.bd.blooddonorfinder.kafka.model.topic.KafkaTopics;
import com.bd.blooddonorfinder.payload.response.GeoResponse;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.GeoStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class UserGeoEnrichedEvent extends BaseEvent {
    private UUID userId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private GeoStatus geoStatus;
    public UserGeoEnrichedEvent(){
        super(KafkaTopics.User_GEO_ENRICHED, "UserGeoEnrichmentEvent");
    }
    public static UserGeoEnrichedEvent from(User user, GeoResponse geoResponse){
        UserGeoEnrichedEvent event = new UserGeoEnrichedEvent();
        event.setUserId(user.getId());
        event.setGeoStatus(GeoStatus.COMPLETED);
        event.setLatitude(geoResponse.getLatitude());
        event.setLongitude(geoResponse.getLongitude());
        event.setAggregateId(String.valueOf(user.getId()));
        event.setVersion(user.getVersion());
        return event;
    }

}
