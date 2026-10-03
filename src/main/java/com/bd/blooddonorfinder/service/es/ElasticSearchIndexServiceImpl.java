package com.bd.blooddonorfinder.service.es;

import com.bd.blooddonorfinder.exception.DocumentMissingException;
import com.bd.blooddonorfinder.kafka.model.events.UserRegisteredEvent;
import com.bd.blooddonorfinder.model.es.documents.RegisteredDonorDocument;
import com.bd.blooddonorfinder.repository.es.RegisteredDonorDocumentRepository;
import com.bd.blooddonorfinder.utils.constants.ElasticIndexes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class ElasticSearchIndexServiceImpl implements ElasticSearchIndexService{
    private final RegisteredDonorDocumentRepository donorDocumentRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    private static final int RETRY_ON_CONFLICT = 3;

    public ElasticSearchIndexServiceImpl(RegisteredDonorDocumentRepository donorDocumentRepository,
                                         ElasticsearchOperations elasticsearchOperations) {
        this.donorDocumentRepository = donorDocumentRepository;
        this.elasticsearchOperations = elasticsearchOperations;
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

    @Override
    public void partialUpdateGeo(UUID userId, BigDecimal latitude, BigDecimal longitude) {
        log.info("Partial update geo starts for userId:{}", userId);
        StopWatch stopWatch = new StopWatch();
        String docId = String.valueOf(userId);
        Map<String, Object> partialDoc = new HashMap<>();
        partialDoc.put("location", Map.of("lat", latitude, "lon", longitude));
        partialDoc.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));

        Document document = Document.from(partialDoc);
        UpdateQuery updateQuery = UpdateQuery.builder(docId)
                .withDocument(document)
                .withRetryOnConflict(RETRY_ON_CONFLICT)
                .withDocAsUpsert(false)
                .build();
        try {
            stopWatch.start();
            elasticsearchOperations.update(updateQuery,
                    IndexCoordinates.of(ElasticIndexes.REGISTERED_DONORS_INDEX));
            stopWatch.stop();
            log.info("Partial geo update applied to elastic for userId={}: lat={}, lon={}, timeTaken: {}ms", userId, latitude, longitude, stopWatch.getTotalTimeMillis());

        } catch (DocumentMissingException e) {
            log.error("INVARIANT VIOLATION: elastic doc missing for userId={} during geo enrichment " +
                    "registration indexing may have failed silently", userId, e);
            throw e;

        } catch (Exception e) {
            log.error("Failed to apply partial geo update in ES for userId={}: {}", userId, e.getMessage(), e);
            throw e;
        }

    }
}
