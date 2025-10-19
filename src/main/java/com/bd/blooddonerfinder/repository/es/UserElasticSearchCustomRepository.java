package com.bd.blooddonerfinder.repository.es;

import com.bd.blooddonerfinder.model.common.ListResponse;
import com.bd.blooddonerfinder.model.es.SearchParam.UserSearchParams;
import com.bd.blooddonerfinder.model.es.documents.UserSearchDocument;
import org.springframework.stereotype.Repository;

@Repository
public interface UserElasticSearchCustomRepository {
    ListResponse<UserSearchDocument>queryForPage(UserSearchParams searchParams);
}
