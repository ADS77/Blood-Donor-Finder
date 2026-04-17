package com.bd.blooddonorfinder.repository.es;

import com.bd.blooddonorfinder.model.es.documents.RegisteredDonorDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface RegisteredDonorDocumentRepository extends ElasticsearchRepository<RegisteredDonorDocument, String> {
    List<RegisteredDonorDocument> findByCityAndBloodGroup(String city, String bloodGroup);
    Optional<RegisteredDonorDocument> findById(String id);

}
