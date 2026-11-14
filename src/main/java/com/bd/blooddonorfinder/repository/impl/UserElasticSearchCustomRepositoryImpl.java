package com.bd.blooddonorfinder.repository.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.bd.blooddonorfinder.exception.ElasticSearchOperationException;
import com.bd.blooddonorfinder.payload.response.ListResponse;
import com.bd.blooddonorfinder.model.es.QueryModel.UserQueryModel;
import com.bd.blooddonorfinder.model.es.SearchParam.GeoDonorSearchParams;
import com.bd.blooddonorfinder.model.es.SearchParam.UserSearchParams;
import com.bd.blooddonorfinder.model.es.documents.DonorSearchDocument;
import com.bd.blooddonorfinder.repository.es.UserElasticSearchCustomRepository;
import com.bd.blooddonorfinder.utils.constants.ElasticIndexes;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
@PropertySource("classpath:application.properties")
public class UserElasticSearchCustomRepositoryImpl implements UserElasticSearchCustomRepository {

    private static final String GEO_LOCATION_FIELD = "location";
    private static final String GEO_UPDATED_AT_FIELD = "geoUpdatedAt";
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    @NotNull
    @Value("${elastic.bdf.donor.search.index}")
    private String indexName;

    @NotNull
    @Value("${elastic.bdf.donor.geo.index:" + ElasticIndexes.REGISTERED_DONORS_INDEX + "}")
    private String geoIndexName;

    private final ObjectMapper objectMapper;
    private final ElasticsearchClient elasticsearchClient;

    public UserElasticSearchCustomRepositoryImpl(ObjectMapper objectMapper, ElasticsearchClient client) {
        this.objectMapper = objectMapper;
        this.elasticsearchClient = client;
    }

    @Override
    public ListResponse<DonorSearchDocument> queryForPage(UserSearchParams searchParams) {
        log.debug("search in elastic for user starts...");
        Query query = (searchParams != null && searchParams.getQueryModel() != null)
                ? UserQueryModel.userQueryModelBuilder(searchParams.getQueryModel())
                : Query.of(q -> q.matchAll(m -> m));
        return executeSearch(indexName, query, searchParams, null);
    }

    @Override
    public ListResponse<DonorSearchDocument> queryForPage(GeoDonorSearchParams searchParams) {
        log.debug("geo search in elastic for user starts...");
        if (searchParams == null || searchParams.getLatitude() == null || searchParams.getLongitude() == null
                || searchParams.getRadiusKm() == null) {
            throw new IllegalArgumentException("latitude, longitude and radiusKm are required for geo donor search");
        }

        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        if (searchParams.getQueryModel() != null) {
            boolBuilder.must(UserQueryModel.userQueryModelBuilder(searchParams.getQueryModel()));
        }
        boolBuilder.filter(f -> f.geoDistance(gd -> gd
                .field(GEO_LOCATION_FIELD)
                .location(loc -> loc.latlon(ll -> ll
                        .lat(searchParams.getLatitude())
                        .lon(searchParams.getLongitude())))
                .distance(searchParams.getRadiusKm() + "km")));

        if (searchParams.getMaxLocationAgeDays() != null && searchParams.getMaxLocationAgeDays() > 0) {
            long cutoffMillis = Instant.now()
                    .minus(Duration.ofDays(searchParams.getMaxLocationAgeDays()))
                    .toEpochMilli();
            boolBuilder.filter(buildFreshnessFilter(cutoffMillis));
        }

        Query query = Query.of(q -> q.bool(boolBuilder.build()));
        log.info("Geo donor search query: center=({},{}) radius={}km maxLocationAgeDays={}",
                searchParams.getLatitude(), searchParams.getLongitude(),
                searchParams.getRadiusKm(), searchParams.getMaxLocationAgeDays());
        return executeSearch(geoIndexName, query, searchParams,
                new double[]{searchParams.getLatitude(), searchParams.getLongitude()});
    }

    /**
     * Location freshness filter: the donor's coordinates must have been confirmed
     * within the freshness window (geoUpdatedAt >= cutoff) OR the doc must predate
     * freshness tracking (geoUpdatedAt missing). The minimum_should_match=1 pattern
     * keeps pre-pipeline documents searchable instead of silently vanishing.
     */
    static Query buildFreshnessFilter(long cutoffMillis) {
        return Query.of(q -> q.bool(b -> b
                .should(s -> s.range(r -> r.untyped(u -> u
                        .field(GEO_UPDATED_AT_FIELD)
                        .gte(JsonData.of(cutoffMillis)))))
                .should(s -> s.bool(nb -> nb.mustNot(mn -> mn.exists(e -> e.field(GEO_UPDATED_AT_FIELD)))))
                .minimumShouldMatch("1")));
    }

    private ListResponse<DonorSearchDocument> executeSearch(String index, Query query,
                                                            UserSearchParams searchParams, double[] geoCenter) {
        ListResponse<DonorSearchDocument> response = new ListResponse<>();
        if (searchParams == null) {
            response.setData(Collections.emptyList());
            response.setCount(0);
            return response;
        }

        int size = (searchParams.getLength() == null || searchParams.getLength() <= 0)
                ? DEFAULT_PAGE_SIZE
                : Math.min(searchParams.getLength(), MAX_PAGE_SIZE);

        SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                .index(index)
                .query(query)
                .size(size);

        if (geoCenter != null) {
            // nearest donors first, stable tie-break by _id for search_after pagination
            searchBuilder
                    .sort(s -> s.geoDistance(g -> g
                            .field(GEO_LOCATION_FIELD)
                            .location(loc -> loc.latlon(ll -> ll.lat(geoCenter[0]).lon(geoCenter[1])))
                            .unit(DistanceUnit.Kilometers)
                            .order(SortOrder.Asc)))
                    .sort(s -> s.field(f -> f.field("_id").order(SortOrder.Asc)));
        } else {
            searchBuilder
                    .sort(s -> s.score(sc -> sc.order(SortOrder.Desc)))
                    .sort(s -> s.field(f -> f.field("_id").order(SortOrder.Asc)));
        }

        if (ArrayUtils.isNotEmpty(searchParams.getSearchAfter())) {
            log.debug("search after request value : {}", Arrays.toString(searchParams.getSearchAfter()));
            List<FieldValue> searchAfterValues = Arrays.stream(searchParams.getSearchAfter())
                    .map(this::toFieldValue)
                    .collect(Collectors.toList());
            searchBuilder.searchAfter(searchAfterValues);
        }

        SearchRequest searchRequest = searchBuilder.build();
        log.debug("Elastic Search Request : {}", searchRequest);
        StopWatch stopWatch = new StopWatch("Paginated Elastic Search");
        stopWatch.start();
        try {
            SearchResponse<ObjectNode> searchResponse = elasticsearchClient.search(
                    searchRequest,
                    ObjectNode.class
            );
            stopWatch.stop();
            response.setTime(stopWatch.getTotalTimeMillis());

            long totalHits = searchResponse.hits().total() != null ? searchResponse.hits().total().value() : 0L;
            response.setCount(totalHits);

            List<DonorSearchDocument> userList = new ArrayList<>();
            List<Hit<ObjectNode>> hits = searchResponse.hits().hits();
            for (Hit<ObjectNode> hit : hits) {
                if (hit.source() != null) {
                    DonorSearchDocument userDoc = objectMapper.convertValue(hit.source(), DonorSearchDocument.class);
                    userList.add(userDoc);
                }
            }

            if (!hits.isEmpty()) {
                Hit<ObjectNode> lastHit = hits.get(hits.size() - 1);
                List<FieldValue> sortValues = lastHit.sort();
                if (sortValues != null && !sortValues.isEmpty()) {
                    Object[] searchAfterArray = sortValues.stream()
                            .map(this::fieldValueToObject)
                            .toArray();
                    response.setSearchAfter(searchAfterArray);
                }
            }

            response.setData(userList);
            log.debug("search in elastic for user ends with {} results, total {}", userList.size(), totalHits);
            return response;
        } catch (IOException e) {
            log.error("Error while searching users from elasticsearch", e);
            throw new ElasticSearchOperationException("Failed to search donors from Elasticsearch", e);
        }
    }

    private FieldValue toFieldValue(Object obj) {
        if (obj instanceof Number) {
            Number num = (Number) obj;
            if (obj instanceof Double || obj instanceof Float) {
                return FieldValue.of(num.doubleValue());
            }
            return FieldValue.of(num.longValue());
        } else if (obj instanceof Boolean) {
            return FieldValue.of((Boolean) obj);
        } else {
            return FieldValue.of(obj == null ? "" : obj.toString());
        }
    }

    private Object fieldValueToObject(FieldValue fv) {
        if (fv == null || fv.isNull()) return null;
        if (fv.isDouble()) return fv.doubleValue();
        if (fv.isLong()) return fv.longValue();
        if (fv.isBoolean()) return fv.booleanValue();
        if (fv.isString()) return fv.stringValue();
        return fv._toJsonString();
    }
}
