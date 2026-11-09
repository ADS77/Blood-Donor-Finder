package com.bd.blooddonorfinder.repository.es;

import com.bd.blooddonorfinder.model.es.documents.DonorSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserElasticSearchRepository extends ElasticsearchRepository<DonorSearchDocument, String> {
    //List<UserSearchDocument> saveAll(List<UserSearchDocument> users);
    List<DonorSearchDocument> findAllByBloodGroup(String bloodGroup);
    //List<UserSearchDocument>findAllByBloodGroupAndDistrict(String bloodGroup, String district);
}
