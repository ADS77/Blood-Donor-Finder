package com.bd.blooddonorfinder.service.es;

import com.bd.blooddonorfinder.model.common.ListResponse;
import com.bd.blooddonorfinder.model.es.SearchParam.UserSearchParams;
import com.bd.blooddonorfinder.model.es.documents.DonorSearchDocument;

import java.util.List;

public interface UserElasticSearchService {

    public ListResponse<DonorSearchDocument> saveAllUsers(List<DonorSearchDocument> users);
    public ListResponse<DonorSearchDocument> findByBloodGroup(String bloodGroup);
    public ListResponse<DonorSearchDocument> queryForPage(UserSearchParams searchParams);


}
