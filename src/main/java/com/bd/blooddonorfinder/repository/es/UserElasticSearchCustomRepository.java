package com.bd.blooddonorfinder.repository.es;

import com.bd.blooddonorfinder.model.common.ListResponse;
import com.bd.blooddonorfinder.model.es.SearchParam.UserSearchParams;
import com.bd.blooddonorfinder.model.es.documents.DonorSearchDocument;
import org.springframework.stereotype.Repository;

@Repository
public interface UserElasticSearchCustomRepository {
    ListResponse<DonorSearchDocument>queryForPage(UserSearchParams searchParams);
}
