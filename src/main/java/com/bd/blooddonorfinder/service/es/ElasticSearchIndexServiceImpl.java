package com.bd.blooddonorfinder.service.es;

import com.bd.blooddonorfinder.kafka.model.events.UserRegisteredEvent;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.es.documents.RegisteredDonorDocument;
import com.bd.blooddonorfinder.repository.UserRepository;
import com.bd.blooddonorfinder.repository.es.RegisteredDonorDocumentRepository;
import com.bd.blooddonorfinder.utils.constants.ElasticIndexes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final UserRepository userRepository;

    private static final int RETRY_ON_CONFLICT = 3;

    public ElasticSearchIndexServiceImpl(RegisteredDonorDocumentRepository donorDocumentRepository,
                                         ElasticsearchOperations elasticsearchOperations,
                                         UserRepository userRepository) {
        this.donorDocumentRepository = donorDocumentRepository;
        this.elasticsearchOperations = elasticsearchOperations;
        this.userRepository = userRepository;
    }

    @Override
    public void indexRegisteredDonor(UserRegisteredEvent event) {
        log.info("..........Indexing registered donor to elastic: userId={}, eventId={} ........"
                ,event.getAggregateId(), event.getEventId() );
        try {
            RegisteredDonorDocument donorDocument = RegisteredDonorDocument.from(event);
            if(donorDocumentRepository.findById(event.getUserId().toString()).isPresent()){
                log.debug("...........Skip indexing, donor exists with id:{}............",event.getUserId());
                return ;
            }
            RegisteredDonorDocument savedDocument = donorDocumentRepository.save(donorDocument);
            log.info("..........Successfully indexed user: userId={}, esId={}.................",
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
        // Freshness stamp: any confirmed geo write (device update or enrichment)
        // makes the donor's position fresh for nearby searches.
        partialDoc.put("geoUpdatedAt", System.currentTimeMillis());

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

        } catch (org.springframework.data.elasticsearch.ResourceNotFoundException e) {
            // Self-heal instead of failing forever: registration indexing may
            // have been lost (e.g. to an ES outage/DLQ), but a location update
            // is still enough information to make the donor searchable. Rebuild
            // the full document from the database, then apply the partial geo
            // write again on top.
            // NOTE: this is the exception Spring Data Elasticsearch actually
            // throws for document_missing_exception (404) — not the local
            // DocumentMissingException class, which nothing ever threw.
            log.warn("Elastic doc missing for userId={} during geo write — healing from the database", userId);
            try {
                indexDonorFromDatabase(userId);
                elasticsearchOperations.update(UpdateQuery.builder(docId)
                                .withDocument(document)
                                .withRetryOnConflict(RETRY_ON_CONFLICT)
                                .withDocAsUpsert(false)
                                .build(),
                        IndexCoordinates.of(ElasticIndexes.REGISTERED_DONORS_INDEX));
                log.info("Healed missing elastic doc for userId={} and applied the geo update", userId);
            } catch (Exception healError) {
                log.error("Could not heal missing elastic doc for userId={}: {}",
                        userId, healError.getMessage(), healError);
                throw e;
            }

        } catch (Exception e) {
            log.error("Failed to apply partial geo update in ES for userId={}: {}", userId, e.getMessage(), e);
            throw e;
        }

    }

    /**
     * Upserts the full donor document from the current database state. Used as
     * the recovery path when a geo write finds the document missing.
     */
    @Transactional(readOnly = true)
    public void indexDonorFromDatabase(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cannot index user " + userId + ": not found in the database"));
        UserRegisteredEvent event = UserRegisteredEvent.from(user);
        event.setGeoUpdatedAt(System.currentTimeMillis());
        donorDocumentRepository.save(RegisteredDonorDocument.from(event));
        log.info("Rebuilt registered-donors document from the database for userId={}", userId);
    }
}
