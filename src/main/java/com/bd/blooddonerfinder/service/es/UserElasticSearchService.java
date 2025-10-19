package com.bd.blooddonerfinder.service.es;

import com.bd.blooddonerfinder.exception.ElasticSearchOperationException;
import com.bd.blooddonerfinder.model.common.ListResponse;
import com.bd.blooddonerfinder.model.es.SearchParam.UserSearchParams;
import com.bd.blooddonerfinder.model.es.documents.UserSearchDocument;
import com.bd.blooddonerfinder.repository.es.UserElasticSearchRepository;
import com.bd.blooddonerfinder.repository.impl.UserElasticSearchCustomRepositoryImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

@Service
@Slf4j
public class UserElasticSearchService {
    private final UserElasticSearchRepository userElasticSearchRepository;
    private final UserElasticSearchCustomRepositoryImpl userElasticSearchCustomRepository;

    public UserElasticSearchService(UserElasticSearchRepository repository, UserElasticSearchCustomRepositoryImpl userElasticSearchCustomRepository){
        this.userElasticSearchRepository = repository;
        this.userElasticSearchCustomRepository = userElasticSearchCustomRepository;
    }

    public ListResponse<UserSearchDocument> saveAllUsers(List<UserSearchDocument> users) {
        ListResponse<UserSearchDocument> response = new ListResponse<>();
        if (users == null || users.isEmpty()) {
            response.setData(Collections.emptyList());
            response.setCount(0);
            return response;
        }
        try {
            StopWatch stopWatch = new StopWatch("saving all users");
            stopWatch.start();
            List<UserSearchDocument> savedUsers = StreamSupport
                    .stream(userElasticSearchRepository.saveAll(users).spliterator(), false)
                    .toList();
            stopWatch.stop();
            response.setData(savedUsers);
            response.setCount(savedUsers.size());
            response.setTime(stopWatch.getTotalTimeMillis());
            log.info("Saved {} users to Elasticsearch in {} ms", savedUsers.size(), stopWatch.getTotalTimeMillis());
        } catch (Exception e) {
            log.error("Error saving users to Elasticsearch", e);
            throw new ElasticSearchOperationException("Failed to save all users to Elasticsearch", e);
        }

        return response;
    }


    public ListResponse<UserSearchDocument> findByBloodGroup(String bloodGroup) {
        ListResponse<UserSearchDocument> response = new ListResponse<>();
        if (bloodGroup == null || bloodGroup.isBlank()) {
            response.setData(Collections.emptyList());
            response.setCount(0);
            return response;
        }
        try {
            log.debug("Searching users in Elasticsearch, blood group: {}", bloodGroup);
            StopWatch stopWatch = new StopWatch("searching user by bloodGroup");
            stopWatch.start();
            List<UserSearchDocument> users = userElasticSearchRepository.findAllByBloodGroup(bloodGroup);
            stopWatch.stop();
            response.setTime(stopWatch.getTotalTimeMillis());
            response.setData(users);
            response.setCount(users.size());
            if (users.isEmpty()) {
                log.info("No users found for blood group {} ({} ms)", bloodGroup, stopWatch.getTotalTimeMillis());
            } else {
                log.info("Found {} users for blood group {} in {} ms", users.size(), bloodGroup, stopWatch.getTotalTimeMillis());
            }
        } catch (Exception e) {
            log.error("Error searching users in Elasticsearch by blood group {}", bloodGroup, e);
            throw new ElasticSearchOperationException("Failed to search users by blood group: " + bloodGroup, e);
        }
        return response;
    }

    public ListResponse<UserSearchDocument> queryForPage(UserSearchParams searchParams){
        return searchParams != null ? userElasticSearchCustomRepository.queryForPage(searchParams) : new ListResponse<>();
    }

}
