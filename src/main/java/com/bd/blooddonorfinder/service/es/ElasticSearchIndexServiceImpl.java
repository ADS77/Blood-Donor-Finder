package com.bd.blooddonorfinder.service.es;

import com.bd.blooddonorfinder.kafka.model.events.UserRegisteredEvent;
import com.bd.blooddonorfinder.model.es.documents.RegisteredDonorDocument;
import com.bd.blooddonorfinder.repository.es.RegisteredDonorDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ElasticSearchIndexServiceImpl implements ElasticSearchIndexService{
    private final RegisteredDonorDocumentRepository donorDocumentRepository;

    public ElasticSearchIndexServiceImpl(RegisteredDonorDocumentRepository donorDocumentRepository) {
        this.donorDocumentRepository = donorDocumentRepository;
    }

    @Override
    public void indexRegisteredDonor(UserRegisteredEvent event) {
        log.info("Indexing registered donor to elastic: userId={}, eventId={}"
                ,event.getAggregateId(), event.getEventId() );
        try {
            RegisteredDonorDocument donorDocument = RegisteredDonorDocument.from(event);
            if(donorDocumentRepository.findById(event.getUserId().toString()).isPresent()){
                log.debug("Skip indexing, donor exists with id:{}",event.getUserId());
                return ;
            }
            RegisteredDonorDocument savedDocument = donorDocumentRepository.save(donorDocument);
            log.info("Successfully indexed user: userId={}, esId={}",
                    event.getUserId(), savedDocument.getId());
        }catch (Exception e){
            log.error("Failed to index user: userId={}, error={}",
                    event.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Elasticsearch indexing failed", e);
        }

    }
}
