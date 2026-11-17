package com.bd.blooddonorfinder.service.es;

import com.bd.blooddonorfinder.model.es.SearchParam.GeoDonorSearchParams;
import com.bd.blooddonorfinder.model.es.SearchParam.UserSearchParams;
import com.bd.blooddonorfinder.model.es.documents.DonorSearchDocument;
import com.bd.blooddonorfinder.model.es.documents.RegisteredDonorDocument;
import com.bd.blooddonorfinder.payload.request.NearbyDonorSearchRequest;
import com.bd.blooddonorfinder.payload.response.NearbyDonorSearchResponse;
import com.bd.blooddonorfinder.repository.es.RegisteredDonorDocumentRepository;
import com.bd.blooddonorfinder.repository.es.UserElasticSearchCustomRepository;
import com.bd.blooddonorfinder.repository.es.UserElasticSearchRepository;
import com.bd.blooddonorfinder.service.geo.DonorGeoRedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NearbyDonorSearchRedisFastPathTest {

    @Mock
    private UserElasticSearchRepository userElasticSearchRepository;
    @Mock
    private UserElasticSearchCustomRepository userElasticSearchCustomRepository;
    @Mock
    private DonorGeoRedisService donorGeoRedisService;
    @Mock
    private RegisteredDonorDocumentRepository registeredDonorDocumentRepository;

    private UserElasticSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserElasticSearchServiceImpl(
                userElasticSearchRepository, userElasticSearchCustomRepository,
                donorGeoRedisService, registeredDonorDocumentRepository);
        ReflectionTestUtils.setField(service, "defaultMaxLocationAgeDays", 90L);
    }

    private NearbyDonorSearchRequest request(Double lat, Double lon) {
        NearbyDonorSearchRequest request = new NearbyDonorSearchRequest();
        request.setLatitude(lat);
        request.setLongitude(lon);
        request.setInitialRadiusKm(5.0);
        request.setMaxRadiusKm(80.0);
        request.setLength(10);
        return request;
    }

    private RegisteredDonorDocument donorDoc(String id, String bloodGroup, Long geoUpdatedAt) {
        RegisteredDonorDocument doc = RegisteredDonorDocument.builder()
                .id(id)
                .username("Test Donor")
                .bloodGroup(bloodGroup)
                .isVerified(true)
                .geoUpdatedAt(geoUpdatedAt)
                .build();
        doc.setLocation(new GeoPoint(23.8, 90.4));
        doc.setCity("Dhaka");
        doc.setDistrict("Dhaka");
        return doc;
    }

    @Test
    void fastPathDisabled_fallsBackToElasticsearch() {
        when(donorGeoRedisService.isEnabled()).thenReturn(false);
        ListResponseStub stub = stubEsEmpty();

        NearbyDonorSearchResponse response = service.searchNearbyDonors(request(23.8, 90.4));

        // the ES expanding-radius loop actually ran
        assertTrue(stub.esQueried);
        verify(donorGeoRedisService, never()).searchNearest(anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void coldRedisSet_fallsBackToElasticsearch() {
        when(donorGeoRedisService.isEnabled()).thenReturn(true);
        when(donorGeoRedisService.searchNearest(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(null);
        stubEsEmpty();

        NearbyDonorSearchResponse response = service.searchNearbyDonors(request(23.8, 90.4));

        verify(userElasticSearchCustomRepository, org.mockito.Mockito.atLeastOnce())
                .queryForPage(any(GeoDonorSearchParams.class));
        assertTrue(response.getDonors().isEmpty());
    }

    @Test
    void fastPathHit_returnsDonorsWithoutEsGeoQuery() {
        when(donorGeoRedisService.isEnabled()).thenReturn(true);
        String donorId = UUID.randomUUID().toString();
        when(donorGeoRedisService.searchNearest(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(donorId));
        when(registeredDonorDocumentRepository.findAllById(anyList()))
                .thenReturn(List.of(donorDoc(donorId, "O+", System.currentTimeMillis())));

        NearbyDonorSearchResponse response = service.searchNearbyDonors(request(23.8, 90.4));

        assertEquals(1, response.getTotalHits());
        assertEquals(donorId, response.getDonors().get(0).getId());
        // contact details never leave the server, even on the fast path
        assertEquals("Test Donor", response.getDonors().get(0).getName());
        verify(userElasticSearchCustomRepository, never()).queryForPage(any(UserSearchParams.class));
        verify(userElasticSearchCustomRepository, never()).queryForPage(any(GeoDonorSearchParams.class));
    }

    @Test
    void fastPathAppliesBloodGroupFilter() {
        when(donorGeoRedisService.isEnabled()).thenReturn(true);
        String donorId = UUID.randomUUID().toString();
        when(donorGeoRedisService.searchNearest(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(donorId));
        when(registeredDonorDocumentRepository.findAllById(anyList()))
                .thenReturn(List.of(donorDoc(donorId, "O+", System.currentTimeMillis())));

        NearbyDonorSearchRequest request = request(23.8, 90.4);
        request.setBloodGroup("AB+");
        stubEsEmpty();

        NearbyDonorSearchResponse response = service.searchNearbyDonors(request);

        assertEquals(0, response.getTotalHits());
        assertTrue(response.getDonors().isEmpty());
    }

    @Test
    void fastPathAppliesFreshnessWindow() {
        when(donorGeoRedisService.isEnabled()).thenReturn(true);
        String donorId = UUID.randomUUID().toString();
        when(donorGeoRedisService.searchNearest(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(donorId));
        // coordinates older than 90 days are excluded, as in the ES query
        long stale = System.currentTimeMillis() - 96L * 24 * 3600 * 1000;
        when(registeredDonorDocumentRepository.findAllById(anyList()))
                .thenReturn(List.of(donorDoc(donorId, "O+", stale)));
        stubEsEmpty();

        NearbyDonorSearchResponse response = service.searchNearbyDonors(request(23.8, 90.4));

        assertEquals(0, response.getTotalHits());
    }

    @Test
    void fastPathSkippedWhenCursorPaginationRequested() {
        when(donorGeoRedisService.isEnabled()).thenReturn(true);
        stubEsEmpty();

        NearbyDonorSearchRequest request = request(23.8, 90.4);
        request.setSearchAfter(new Object[]{"10.5", "cursor"});

        service.searchNearbyDonors(request);

        verify(userElasticSearchCustomRepository, org.mockito.Mockito.atLeastOnce())
                .queryForPage(any(GeoDonorSearchParams.class));
        verify(donorGeoRedisService, never()).searchNearest(anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void fastPathSkippedWhenAvailabilityFilterRequested() {
        when(donorGeoRedisService.isEnabled()).thenReturn(true);
        stubEsEmpty();

        NearbyDonorSearchRequest request = request(23.8, 90.4);
        request.setIsAvailable(true);

        service.searchNearbyDonors(request);

        verify(userElasticSearchCustomRepository, org.mockito.Mockito.atLeastOnce())
                .queryForPage(any(GeoDonorSearchParams.class));
        verify(donorGeoRedisService, never()).searchNearest(anyDouble(), anyDouble(), anyDouble());
    }

    /** Wires the ES fallback loop to return an empty result on every probe. */
    private ListResponseStub stubEsEmpty() {
        ListResponseStub stub = new ListResponseStub();
        when(userElasticSearchCustomRepository.queryForPage(any(GeoDonorSearchParams.class)))
                .thenAnswer(inv -> stub.empty());
        return stub;
    }

    /** Tiny holder so assertions can see the ES loop actually ran. */
    private static final class ListResponseStub {
        boolean esQueried;

        com.bd.blooddonorfinder.payload.response.ListResponse<DonorSearchDocument> empty() {
            esQueried = true;
            com.bd.blooddonorfinder.payload.response.ListResponse<DonorSearchDocument> response =
                    new com.bd.blooddonorfinder.payload.response.ListResponse<>();
            response.setData(List.of());
            response.setCount(0);
            return response;
        }
    }
}
