package com.bd.blooddonorfinder.repository.impl;

import com.bd.blooddonorfinder.model.es.Location;
import com.bd.blooddonorfinder.model.es.documents.DonorSearchDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The registered-donors index is written by RegisteredDonorDocument, whose shape
 * differs from the search read model: the display name lives in "username" and
 * city/district/zipcode sit at the top level while the response nests them
 * inside Location. The backfill step must copy those fields across without
 * overwriting values that are already present.
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
        DonorSearchDocument doc = DonorSearchDocument.builder().location(location).build();
        UserElasticSearchCustomRepositoryImpl.backfillFromSource(source, doc);

        assertEquals("Khilkhet", location.getCity());
        assertEquals("Dhaka", location.getDistrict());
        assertEquals("1229", location.getZipcode());
    }

    @Test
    void backfillsNameFromUsername() throws Exception {
        JsonNode source = objectMapper.readTree("""
                { "username": "Ankur", "name": null }
                """);

        DonorSearchDocument doc = DonorSearchDocument.builder().build();
        UserElasticSearchCustomRepositoryImpl.backfillFromSource(source, doc);

        assertEquals("Ankur", doc.getName());
    }

    @Test
    void backfillKeepsExistingNestedValues() throws Exception {
        JsonNode source = objectMapper.readTree("""
                { "username": "FromIndex", "city": "FromIndex", "district": null }
                """);

        Location location = Location.builder().city("AlreadySet").build();
        DonorSearchDocument doc = DonorSearchDocument.builder()
                .name("AlreadySet").location(location).build();
        UserElasticSearchCustomRepositoryImpl.backfillFromSource(source, doc);

        assertEquals("AlreadySet", doc.getName());
        assertEquals("AlreadySet", location.getCity());
        assertNull(location.getDistrict());
    }

    @Test
    void backfillToleratesMissingOrNullLocation() throws Exception {
        JsonNode source = objectMapper.readTree("{ \"city\": \"Khilkhet\" }");

        UserElasticSearchCustomRepositoryImpl.backfillFromSource(source, DonorSearchDocument.builder().build());
        UserElasticSearchCustomRepositoryImpl.backfillFromSource(null, DonorSearchDocument.builder().build());
    }
}
