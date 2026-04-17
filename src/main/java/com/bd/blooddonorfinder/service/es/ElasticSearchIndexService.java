package com.bd.blooddonorfinder.service.es;

import com.bd.blooddonorfinder.kafka.model.events.UserRegisteredEvent;


public interface ElasticSearchIndexService {

    public void indexRegisteredDonor(UserRegisteredEvent event);
}
