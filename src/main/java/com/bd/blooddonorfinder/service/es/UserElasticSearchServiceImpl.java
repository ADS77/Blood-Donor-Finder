package com.bd.blooddonorfinder.service.es;

import com.bd.blooddonorfinder.exception.ElasticSearchOperationException;
import com.bd.blooddonorfinder.model.common.ListResponse;
import com.bd.blooddonorfinder.model.es.SearchParam.UserSearchParams;
import com.bd.blooddonorfinder.model.es.documents.DonorSearchDocument;
import com.bd.blooddonorfinder.repository.es.UserElasticSearchCustomRepository;
import com.bd.blooddonorfinder.repository.es.UserElasticSearchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

@Service
@Slf4j
public class UserElasticSearchServiceImpl implements UserElasticSearchService {

    private final UserElasticSearchRepository userElasticSearchRepository;
    private final UserElasticSearchCustomRepository userElasticSearchCustomRepository;

    public UserElasticSearchServiceImpl(UserElasticSearchRepository userElasticSearchRepository,
                                        UserElasticSearchCustomRepository userElasticSearchCustomRepository) {
        this.userElasticSearchRepository = userElasticSearchRepository;
        this.userElasticSearchCustomRepository = userElasticSearchCustomRepository;
    }

    @Override
    public ListResponse<DonorSearchDocument> saveAllUsers(List<DonorSearchDocument> users) {
            ListResponse<DonorSearchDocument> response = new ListResponse<>();
            if (users == null || users.isEmpty()) {
                response.setData(Collections.emptyList());
                response.setCount(0);
                return response;
            }
            try {
                StopWatch stopWatch = new StopWatch("saving all users");
                stopWatch.start();
                List<DonorSearchDocument> savedUsers = StreamSupport
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

    @Override
    public ListResponse<DonorSearchDocument> findByBloodGroup(String bloodGroup) {
        ListResponse<DonorSearchDocument> response = new ListResponse<>();
        if (bloodGroup == null || bloodGroup.isBlank()) {
            response.setData(Collections.emptyList());
            response.setCount(0);
            return response;
        }
        try {
            log.debug("Searching users in Elasticsearch, blood group: {}", bloodGroup);
            StopWatch stopWatch = new StopWatch("searching user by bloodGroup");
            stopWatch.start();
            List<DonorSearchDocument> users = userElasticSearchRepository.findAllByBloodGroup(bloodGroup);
            stopWatch.stop();
            response.setTime(stopWatch.getTotalTimeMillis());

            if (users.isEmpty()) {
                log.info("No users found for blood group {} ({} ms)", bloodGroup, stopWatch.getTotalTimeMillis());
            } else {
                response.setData(users);
                response.setCount(users.size());
                log.info("Found {} users for blood group {} in {} ms", users.size(), bloodGroup, stopWatch.getTotalTimeMillis());
            }
        } catch (Exception e) {
            log.error("Error searching users in Elasticsearch by blood group {}", bloodGroup, e);
            throw new ElasticSearchOperationException("Failed to search users by blood group: " + bloodGroup, e);
        }
        return response;
    }

    @Override
    public ListResponse<DonorSearchDocument> queryForPage(UserSearchParams searchParams) {
        return searchParams != null ? userElasticSearchCustomRepository.queryForPage(searchParams) : new ListResponse<>();
    }
}
