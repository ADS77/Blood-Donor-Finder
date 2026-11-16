package com.bd.blooddonorfinder.model.es;

import com.bd.blooddonorfinder.model.es.documents.DonorSearchDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The registered-donors index stores coordinates in geo_point form
 * ({"lat":..,"lon":..}, see RegisteredDonorDocument) while the search read model
 * uses latitude/longitude. Both shapes must map onto Location, and the
 * top-level address fields of the index must not break deserialization.
 */
class LocationDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesGeoPointShapeFromElasticsearchIndex() throws Exception {
        // exact shape of a registered_donors hit source
        String json = """
                {
                  "id": "ce1481b6-3d8c-416d-a6ae-e8e992dabfe4",
                  "username": "Ankur",
                  "bloodGroup": "AB_POSITIVE",
                  "city": "Khilkhet",
                  "district": "Dhaka",
                  "country": "Bangladesh",
                  "location": { "lat": 23.8351324, "lon": 90.4351121 },
                  "isVerified": true,
                  "geoUpdatedAt": 1763123456000,
                  "_class": "com.bd.blooddonorfinder.model.es.documents.RegisteredDonorDocument"
                }
                """;

        DonorSearchDocument doc = objectMapper.readValue(json, DonorSearchDocument.class);

        assertEquals("ce1481b6-3d8c-416d-a6ae-e8e992dabfe4", doc.getId());
        assertEquals(23.8351324, doc.getLocation().getLatitude(), 1e-9);
        assertEquals(90.4351121, doc.getLocation().getLongitude(), 1e-9);
    }

    @Test
    void deserializesLatitudeLongitudeShape() throws Exception {
        String json = """
                { "latitude": 23.81, "longitude": 90.41, "city": "Dhaka" }
                """;

        Location location = objectMapper.readValue(json, Location.class);

        assertEquals(23.81, location.getLatitude(), 1e-9);
        assertEquals(90.41, location.getLongitude(), 1e-9);
        assertEquals("Dhaka", location.getCity());
    }

    @Test
    void serializesWithLatitudeLongitudeKeys() throws Exception {
        Location location = Location.builder()
                .latitude(23.81)
                .longitude(90.41)
                .city("Dhaka")
                .build();

        String json = objectMapper.writeValueAsString(location);

        assertEquals(true, json.contains("\"latitude\":23.81"));
        assertEquals(true, json.contains("\"longitude\":90.41"));
    }
}
