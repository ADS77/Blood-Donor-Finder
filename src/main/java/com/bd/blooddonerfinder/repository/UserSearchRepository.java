package com.bd.blooddonerfinder.repository;

import com.bd.blooddonerfinder.model.common.ListResponse;
import com.bd.blooddonerfinder.model.es.SearchParam.UserSearchParams;
import com.bd.blooddonerfinder.model.es.documents.UserSearchDocument;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSearchRepository{
    ListResponse<UserSearchDocument>queryForPage(UserSearchParams searchParams);
}
