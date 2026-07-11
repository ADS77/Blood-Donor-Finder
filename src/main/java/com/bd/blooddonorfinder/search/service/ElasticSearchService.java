/*
package com.bd.blooddonorfinder.search.service;

import com.bd.blooddonorfinder.search.dto.SearchRequest;
import com.bd.blooddonorfinder.search.model.DonorDocument;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.retry.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ElasticSearchService {

    private final ElasticsearchOperations elasticOperations;
    private final MeterRegistry meterRegistry;

    @Value("${app.search.max.raw.results}")
    private int maxRawResult;

    @CircuitBreaker(name = "elasticsearch", fallbackMethod = "searchNearbyFallback")
    @Timed(value = "geo.search.latency.elasticsearch",
            description = "ElasticSearch geo_distance query latency",
            percentiles = {0.5, 0.9, 0.99})
    public List<DonorDocument> searchNearby(String bloodGroup, double lat, double lng,
                                            int radiusKm, SearchRequest request) {

        var nativeQuery = buildGeoQuery(bloodGroup, lat, lng, radiusKm, request);

        SearchHits<DonorDocument> hits = elasticOperations.search(nativeQuery, DonorDocument.class);

        List<DonorDocument> results = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();

        log.debug("ES found {} donors for group={} radius={}km",
                results.size(), bloodGroup, radiusKm);

        meterRegistry.gauge("geo.search.result_count",
                List.of(io.micrometer.core.instrument.Tag.of("blood_group", bloodGroup),
                        io.micrometer.core.instrument.Tag.of("path", "es")),
                results.size());

        return results;
    }

}
*/
