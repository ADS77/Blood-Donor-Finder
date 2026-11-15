package com.bd.blooddonorfinder.service.es;

import com.bd.blooddonorfinder.payload.request.NearbyDonorSearchRequest;
import com.bd.blooddonorfinder.payload.response.ListResponse;
import com.bd.blooddonorfinder.payload.response.NearbyDonorSearchResponse;
import com.bd.blooddonorfinder.model.es.SearchParam.UserSearchParams;
import com.bd.blooddonorfinder.model.es.documents.DonorSearchDocument;

import java.util.List;

public interface UserElasticSearchService {

    public ListResponse<DonorSearchDocument> saveAllUsers(List<DonorSearchDocument> users);
    public ListResponse<DonorSearchDocument> findByBloodGroup(String bloodGroup);
    public ListResponse<DonorSearchDocument> queryForPage(UserSearchParams searchParams);

    /**
     * Searches donors around a geo point. Starts with the initial radius (default 5 km) and
     * expands the radius step by step (default 5 km) up to maxExpansions times (default 4)
     * until at least one donor is found.
     */
    NearbyDonorSearchResponse searchNearbyDonors(NearbyDonorSearchRequest request);
}
