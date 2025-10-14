package com.bd.blooddonerfinder.util;


import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.json.JsonData;
import com.bd.blooddonerfinder.model.common.SearchCriteria;
import com.bd.blooddonerfinder.model.common.SearchRange;
import com.bd.blooddonerfinder.model.common.SearchType;

public class ElasticQueryUtils {
    private static final String EXACT_SUFFIX = "keyword";
    private static final String BETWEEN_PREFIX = "prefix";
    private static final String BETWEEN_FROM = "from";
    private static final String BETWEEN_TO = "to";


    public static Query getFieldQueryBuilder(SearchCriteria criteria, String fieldName, boolean isAnalyzedField) {
        BoolQuery.Builder queryBuilder = new BoolQuery.Builder();
        SearchType searchType = criteria.getSearchType();

        if (searchType == SearchType.masked) {
            if (isAnalyzedField) {
                fieldName = fieldName + "." + EXACT_SUFFIX;
            }
            String finalFieldName = fieldName;
            for (String pattern : criteria.getPatterns()) {
                queryBuilder.should(q -> q.regexp(r -> r
                        .field(finalFieldName)
                        .value(pattern)));
            }

        } else if (searchType == SearchType.range) {
            String finalFieldName = fieldName;
            for (SearchRange range : criteria.getRanges()) {
                queryBuilder.should(q -> q.range(r -> r
                        .field(finalFieldName)
                        .gte(JsonData.of(range.getMin()))
                        .lte(JsonData.of(range.getMax()))));
            }

        } else if (searchType == SearchType.exact) {
            if (isAnalyzedField) {
                fieldName = fieldName + "." + EXACT_SUFFIX;
            }
            String finalFieldName = fieldName;
            for (String value : criteria.getValues()) {
                queryBuilder.should(q -> q.term(t -> t
                        .field(finalFieldName)
                        .value(FieldValue.of(value))));
            }

        } else if (searchType == SearchType.partial) {
            String finalFieldName = fieldName;
            for (String value : criteria.getValues()) {
                queryBuilder.should(q -> q.match(m -> m
                        .field(finalFieldName)
                        .query(value)
                        .operator(Operator.And)));
            }

        } else if (searchType == SearchType.phrase) {
            String finalFieldName = fieldName;
            for (String value : criteria.getValues()) {
                queryBuilder.should(q -> q.matchPhrase(mp -> mp
                        .field(finalFieldName)
                        .query(value)
                        .slop(2)));
            }

        } else if (searchType == SearchType.between) {
            String finalFieldName = fieldName;
            for (SearchRange range : criteria.getRanges()) {
                String[] from = range.getMin().split(" ");
                String[] to = range.getMax().split(" ");
                if (from.length == 2 && to.length == 2 && from[0].equals(to[0])) {
                    String prefix = from[0];
                    long fromNum = Long.parseLong(from[1]);
                    long toNum = Long.parseLong(to[1]);

                    if (fromNum <= toNum) {
                        queryBuilder.should(q -> q.bool(bq -> bq
                                .must(mq -> mq.term(tq -> tq
                                        .field(finalFieldName + "." + BETWEEN_PREFIX)
                                        .value(FieldValue.of(prefix))))
                                .must(mq -> mq.range(rq -> rq
                                        .field(finalFieldName + "." + BETWEEN_FROM)
                                        .lte(JsonData.of(fromNum))))
                                .must(mq -> mq.range(rq -> rq
                                        .field(finalFieldName + "." + BETWEEN_TO)
                                        .gte(JsonData.of(toNum))))));
                    }
                }
            }

        } else if (searchType == SearchType.wildcard) {
            String finalFieldName = fieldName;
            for (String value : criteria.getValues()) {
                queryBuilder.should(q -> q.term(t -> t
                        .field(finalFieldName)
                        .value(FieldValue.of(value))));
                queryBuilder.should(q -> q.wildcard(w -> w
                        .field(finalFieldName)
                        .value(value + "*")));
                queryBuilder.should(q -> q.fuzzy(f -> f
                        .field(finalFieldName)
                        .value(value)
                        .fuzziness("1")
                        .boost(0.1f)));
            }
        }

        return queryBuilder.build()._toQuery();
    }


}
