package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.exception.GeoLocationException;
import com.bd.blooddonorfinder.model.GeoResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
@Slf4j
public class GeoLocationService {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    @Value("${geo.fetch.lat.long.url}")
    private String URL_PREFIX;

    public GeoLocationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    @Cacheable(
            value = "geolocations",
            key = "#city",
            sync = true
    )
    public GeoResponse getLatLong(String city){
        log.info("No geo-location provided, came to search geo location for city : {}", city);
        GeoResponse geoResponse = new GeoResponse();
        try {
            String url = URL_PREFIX
                    + URLEncoder.encode(city, StandardCharsets.UTF_8)
                    + "&limit=1";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Sondhan/1.0 (A voluntary project team to search nearby blood donors)")
                    .GET()
                    .timeout(TIMEOUT)
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new GeoLocationException(
                        "Failed with status " + response.statusCode()
                );
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root.isArray() && root.size() > 0) {
                log.info("Geolocation found for city : {}", city);
                JsonNode node = root.get(0);
                String lat = node.get("lat").asText();
                String lon = node.get("lon").asText();
                geoResponse.setSuccess(true);
                geoResponse.setLatitude(BigDecimal.valueOf(Double.valueOf(lat)));
                geoResponse.setLongitude(BigDecimal.valueOf(Double.valueOf(lon)));
                return geoResponse;
            }
            log.error("No geo location result found for city: " + city);
            return geoResponse;
        } catch (IOException e) {
            geoResponse.setSuccess(false);
            throw new GeoLocationException("Network error", e);
        } catch (InterruptedException e) {
            geoResponse.setSuccess(false);
            Thread.currentThread().interrupt();
            throw new GeoLocationException("Request interrupted", e);
        }
    }
}
