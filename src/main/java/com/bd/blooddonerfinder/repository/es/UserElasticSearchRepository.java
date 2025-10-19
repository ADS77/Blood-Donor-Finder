package com.bd.blooddonerfinder.repository.es;

import com.bd.blooddonerfinder.model.es.documents.UserSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserElasticSearchRepository extends ElasticsearchRepository<UserSearchDocument, String> {
    //List<UserSearchDocument> saveAll(List<UserSearchDocument> users);
    List<UserSearchDocument> findAllByBloodGroup(String bloodGroup);
    //List<UserSearchDocument>findAllByBloodGroupAndDistrict(String bloodGroup, String district);
}
