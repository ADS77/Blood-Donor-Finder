package com.bd.blooddonorfinder.service.es;

import com.bd.blooddonorfinder.kafka.model.events.UserRegisteredEvent;

import java.math.BigDecimal;
import java.util.UUID;


public interface ElasticSearchIndexService {

    public void indexRegisteredDonor(UserRegisteredEvent event);

    void partialUpdateGeo(UUID userId, BigDecimal latitude, BigDecimal longitude);
}
