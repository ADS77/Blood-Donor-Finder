package com.bd.blooddonerfinder.util;


import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.bd.blooddonerfinder.model.es.common.FieldType;
import com.bd.blooddonerfinder.model.es.common.SearchCriteria;
import com.bd.blooddonerfinder.model.es.common.SearchRange;
import com.bd.blooddonerfinder.model.es.common.SearchType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
@Slf4j
public class ElasticQueryUtils {

    public static Query getFieldQueryBuilder(SearchCriteria criteria, String fieldName, boolean isAnalyzedField) {
        List<Query> shouldQueries = new ArrayList<>();
        SearchType searchType = criteria.getSearchType();

        switch (searchType) {
            case masked:
                shouldQueries.addAll(buildMaskedQuery(criteria, fieldName, isAnalyzedField));
                break;
            case range:
                shouldQueries.addAll(buildRangeQuery(criteria, fieldName));
                break;
            case exact:
                shouldQueries.addAll(buildExactQuery(criteria, fieldName, isAnalyzedField));
                break;
            case partial:
                shouldQueries.addAll(buildPartialQuery(criteria, fieldName));
                break;
            case phrase:
                shouldQueries.addAll(buildPhraseQuery(criteria, fieldName));
                break;
            case between:
                shouldQueries.addAll(buildBetweenQuery(criteria, fieldName));
                break;
            case lessThan:
                shouldQueries.add(buildLessThanQuery(criteria, fieldName));
                break;
        }

        return Query.of(q -> q.bool(b -> b.should(shouldQueries)));
    }

    private static List<Query> buildMaskedQuery(SearchCriteria criteria, String fieldName, boolean isAnalyzedField) {
        List<Query> queries = new ArrayList<>();
        String finalFieldName = isAnalyzedField ? fieldName + ".keyword" : fieldName;

        for (String pattern : criteria.getPatterns()) {
            queries.add(Query.of(q -> q.regexp(r -> r
                    .field(finalFieldName)
                    .value(pattern)
            )));
        }
        return queries;
    }

    private static List<Query> buildRangeQuery(SearchCriteria criteria, String fieldName) {
        List<Query> queries = new ArrayList<>();

        for (SearchRange range : criteria.getRanges()) {
            Query query = switch (criteria.getFieldType()) {
                case NUMBER -> Query.of(q -> q.range(r -> r
                        .number(n -> n
                                .field(fieldName)
                                .gte(Double.parseDouble(range.getMin()))
                                .lte(Double.parseDouble(range.getMax()))
                        )
                ));
                case DATE -> Query.of(q -> q.range(r -> r
                        .date(d -> d
                                .field(fieldName)
                                .gte(range.getMin())
                                .lte(range.getMax())
                        )
                ));
                case KEYWORD, TEXT -> Query.of(q -> q.range(r -> r
                        .term(t -> t
                                .field(fieldName)
                                .gte(range.getMin())
                                .lte(range.getMax())
                        )
                ));
            };
            queries.add(query);
        }
        return queries;
    }

    private static List<Query> buildExactQuery(SearchCriteria criteria, String fieldName, boolean isAnalyzedField) {
        List<Query> queries = new ArrayList<>();
        String finalFieldName = isAnalyzedField ? fieldName + ".keyword" : fieldName;

        for (String value : criteria.getValues()) {
            queries.add(Query.of(q -> q.term(t -> t
                    .field(finalFieldName)
                    .value(value)
            )));
        }
        return queries;
    }

    private static List<Query> buildPartialQuery(SearchCriteria criteria, String fieldName) {
        List<Query> queries = new ArrayList<>();

        for (String value : criteria.getValues()) {
            queries.add(Query.of(q -> q.match(m -> m
                    .field(fieldName)
                    .query(value)
                    .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
            )));
        }
        return queries;
    }

    private static List<Query> buildPhraseQuery(SearchCriteria criteria, String fieldName) {
        List<Query> queries = new ArrayList<>();

        for (String value : criteria.getValues()) {
            queries.add(Query.of(q -> q.matchPhrase(mp -> mp
                    .field(fieldName)
                    .query(value)
                    .slop(2)
            )));
        }
        return queries;
    }

    private static List<Query> buildBetweenQuery(SearchCriteria criteria, String fieldName) {
        List<Query> queries = new ArrayList<>();

        for (SearchRange range : criteria.getRanges()) {
            String[] from = range.getMin().split(" ");
            String[] to = range.getMax().split(" ");

            if (from.length == 2 && to.length == 2 && from[0].equals(to[0])) {
                String prefix = from[0];

                try {
                    long fromNum = Long.parseLong(from[1]);
                    long toNum = Long.parseLong(to[1]);

                    if (fromNum <= toNum) {
                        FieldType rangeFieldType = criteria.getFieldType() != null ?
                                criteria.getFieldType() : FieldType.NUMBER;

                        Query betweenQuery = buildBetweenQueryByType(
                                fieldName, prefix, fromNum, toNum, rangeFieldType
                        );
                        queries.add(betweenQuery);
                    }
                } catch (NumberFormatException e) {
                    log.error("Invalid number format in between query for field: {}, range: {} - {}",
                            fieldName, range.getMin(), range.getMax(), e);
                }
            }
        }
        return queries;
    }

    private static Query buildBetweenQueryByType(String fieldName, String prefix,
                                                 long fromNum, long toNum,
                                                 FieldType fieldType) {
        return switch (fieldType) {
            case NUMBER -> Query.of(q -> q.bool(b -> b
                    .must(m -> m.term(t -> t
                            .field(fieldName + ".prefix")
                            .value(prefix)
                    ))
                    .must(m -> m.range(r -> r
                            .number(n -> n
                                    .field(fieldName + ".from")
                                    .lte((double) fromNum)
                            )
                    ))
                    .must(m -> m.range(r -> r
                            .number(n -> n
                                    .field(fieldName + ".to")
                                    .gte((double) toNum)
                            )
                    ))
            ));

            case DATE -> Query.of(q -> q.bool(b -> b
                    .must(m -> m.term(t -> t
                            .field(fieldName + ".prefix")
                            .value(prefix)
                    ))
                    .must(m -> m.range(r -> r
                            .date(d -> d
                                    .field(fieldName + ".from")
                                    .lte(String.valueOf(fromNum))
                            )
                    ))
                    .must(m -> m.range(r -> r
                            .date(d -> d
                                    .field(fieldName + ".to")
                                    .gte(String.valueOf(toNum))
                            )
                    ))
            ));

            default -> Query.of(q -> q.bool(b -> b
                    .must(m -> m.term(t -> t
                            .field(fieldName + ".prefix")
                            .value(prefix)
                    ))
                    .must(m -> m.range(r -> r
                            .term(t -> t
                                    .field(fieldName + ".from")
                                    .lte(String.valueOf(fromNum))
                            )
                    ))
                    .must(m -> m.range(r -> r
                            .term(t -> t
                                    .field(fieldName + ".to")
                                    .gte(String.valueOf(toNum))
                            )
                    ))
            ));
        };
    }
    private static Query buildLessThanQuery(SearchCriteria criteria, String fieldName) {
        String value = criteria.getValues().get(0);
        FieldType fieldType = criteria.getFieldType();
        return switch (fieldType) {
            case NUMBER -> {
                try {
                    double numValue = Double.parseDouble(value);
                    yield Query.of(q -> q.range(r -> r
                            .number(n -> n
                                    .field(fieldName)
                                    .lt(numValue)
                            )
                    ));
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse '{}' as number, falling back to term query", value);
                    yield Query.of(q -> q.range(r -> r
                            .term(t -> t
                                    .field(fieldName)
                                    .lt(value)
                            )
                    ));
                }
            }

            case DATE -> Query.of(q -> q.range(r -> r
                    .date(d -> d
                            .field(fieldName)
                            .lt(value)
                    )
            ));

            default -> Query.of(q -> q.range(r -> r
                    .term(t -> t
                            .field(fieldName)
                            .lt(value)
                    )
            ));
        };
    }

}
