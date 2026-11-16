package com.bd.blooddonorfinder.repository.impl;

import com.bd.blooddonorfinder.model.es.Location;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The registered-donors index keeps city/district/zipcode at the top level of
 * the document while the search response nests them inside Location. The
 * backfill step must copy the top-level fields across without overwriting
 * values that are already present.
 */
class UserElasticSearchCustomRepositoryImplLocationBackfillTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void backfillsTopLevelAddressFieldsIntoNestedLocation() throws Exception {
        JsonNode source = objectMapper.readTree("""
                {
                  "city": "Khilkhet",
                  "district": "Dhaka",
                  "zipcode": "1229",
                  "location": { "lat": 23.83, "lon": 90.43 }
                }
                """);

        Location location = Location.builder().latitude(23.83).longitude(90.43).build();
        UserElasticSearchCustomRepositoryImpl.backfillLocationFromSource(source, location);

        assertEquals("Khilkhet", location.getCity());
        assertEquals("Dhaka", location.getDistrict());
        assertEquals("1229", location.getZipcode());
    }

    @Test
    void backfillKeepsExistingNestedValues() throws Exception {
        JsonNode source = objectMapper.readTree("""
                { "city": "FromIndex", "district": null }
                """);

        Location location = Location.builder().city("AlreadySet").build();
        UserElasticSearchCustomRepositoryImpl.backfillLocationFromSource(source, location);

        assertEquals("AlreadySet", location.getCity());
        assertNull(location.getDistrict());
    }

    @Test
    void backfillToleratesMissingOrNullLocation() throws Exception {
        JsonNode source = objectMapper.readTree("{ \"city\": \"Khilkhet\" }");

        UserElasticSearchCustomRepositoryImpl.backfillLocationFromSource(source, null);
        UserElasticSearchCustomRepositoryImpl.backfillLocationFromSource(null, Location.builder().build());
    }
}
