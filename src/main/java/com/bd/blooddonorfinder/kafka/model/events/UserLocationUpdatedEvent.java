package com.bd.blooddonorfinder.kafka.model.events;

import com.bd.blooddonorfinder.kafka.model.BaseEvent;
import com.bd.blooddonorfinder.kafka.model.topic.KafkaTopics;
import com.bd.blooddonorfinder.model.common.User;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Fired after a donor confirms a new position from their device. Consumed to
 * propagate the fresh coordinates (plus the {@code geoUpdatedAt} freshness stamp)
 * into Elasticsearch.
 */
@Getter
@Setter
@ToString(callSuper = true)
public class UserLocationUpdatedEvent extends BaseEvent {
    private UUID userId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    /** Epoch-millis of the confirmed position — mirrors app_user.geo_updated_at. */
    private Long geoUpdatedAt;

    public UserLocationUpdatedEvent() {
        super(KafkaTopics.USER_LOCATION_UPDATED, "UserLocationUpdatedEvent");
    }

    public static UserLocationUpdatedEvent of(User user, Instant geoUpdatedAt) {
        UserLocationUpdatedEvent event = new UserLocationUpdatedEvent();
        event.setUserId(user.getId());
        if (user.getGeoLocation() != null) {
            event.setLatitude(user.getGeoLocation().getLatitude());
            event.setLongitude(user.getGeoLocation().getLongitude());
        }
        event.setGeoUpdatedAt(geoUpdatedAt.toEpochMilli());
        event.setAggregateId(String.valueOf(user.getId()));
        // defensive: JPA @Version is null only for non-managed instances (e.g. tests)
        event.setVersion(user.getVersion() != null ? user.getVersion() : 0L);
        return event;
    }
}
