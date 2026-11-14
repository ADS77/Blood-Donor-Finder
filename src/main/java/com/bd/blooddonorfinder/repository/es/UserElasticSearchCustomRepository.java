package com.bd.blooddonorfinder.repository.es;

import com.bd.blooddonorfinder.model.es.SearchParam.GeoDonorSearchParams;
import com.bd.blooddonorfinder.payload.response.ListResponse;
import com.bd.blooddonorfinder.model.es.SearchParam.UserSearchParams;
import com.bd.blooddonorfinder.model.es.documents.DonorSearchDocument;
import org.springframework.stereotype.Repository;

@Repository
public interface UserElasticSearchCustomRepository {
    ListResponse<DonorSearchDocument> queryForPage(UserSearchParams searchParams);

    /** Geo distance search with expanding radius support. Radius in km. */
    ListResponse<DonorSearchDocument> queryForPage(GeoDonorSearchParams searchParams);
}
