package com.bd.blooddonerfinder.repository.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.bd.blooddonerfinder.model.common.ListResponse;
import com.bd.blooddonerfinder.model.es.QueryModel.UserQueryModel;
import com.bd.blooddonerfinder.model.es.SearchParam.UserSearchParams;
import com.bd.blooddonerfinder.model.es.documents.UserSearchDocument;
import com.bd.blooddonerfinder.repository.UserSearchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
@PropertySource("classpath.application.properties")
public class UserSearchRepositoryImpl implements UserSearchRepository {
    @NotNull
    @Value("${elastic.bdf.user.search.index}")
    private String indexName;

    private final ObjectMapper objectMapper;
    private final ElasticsearchClient elasticsearchClient;

    public UserSearchRepositoryImpl(ObjectMapper objectMapper, ElasticsearchClient client) {
        this.objectMapper = objectMapper;
        this.elasticsearchClient = client;
    }

    @Override
    public ListResponse<UserSearchDocument> queryForPage(UserSearchParams searchParams) {
        log.debug("search in elastic for user starts");
        ListResponse<UserSearchDocument> response = new ListResponse<>();
        List<UserSearchDocument> userList = new ArrayList<>();
        Query query = UserQueryModel.userQueryModelBuilder(searchParams.getQueryModel());
        log.debug("Query : {}", query);

        SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                .index(indexName)
                .query(query)
                .size(searchParams.getLength())
                .sort(s -> s.score(sc -> sc.order(SortOrder.Desc)))
                .sort(s -> s.field(f -> f.field("_id").order(SortOrder.Asc)));
        if(ArrayUtils.isNotEmpty(searchParams.getSearchAfter())){
            log.debug("search after request value : {}", searchParams.getSearchAfter());
            List<FieldValue>searchAfterValues = Arrays.stream(searchParams.getSearchAfter())
                    .map( obj -> {
                        if (obj instanceof Number){
                            return FieldValue.of(((Number)obj).longValue());
                        }else {
                            return FieldValue.of(obj.toString());
                        }
                    })
                    .collect(Collectors.toList());
            searchBuilder.searchAfter(searchAfterValues);

            SearchRequest searchRequest = searchBuilder.build();
            log.debug("Elastic Search After Query : {}", searchRequest);
            StopWatch stopWatch = new StopWatch("Paginated Elastic Search with Search After");
            stopWatch.start();
            try {
                SearchResponse<ObjectNode> searchResponse = elasticsearchClient.search(
                        searchRequest,
                        ObjectNode.class
                );
                long totalHits = searchResponse.hits().total().value();
                log.debug("Total Count : {}", totalHits);
                response.setCount(totalHits);
                List<Hit<ObjectNode>> hits = searchResponse.hits().hits();
                for(Hit<ObjectNode> hit : hits){

                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
