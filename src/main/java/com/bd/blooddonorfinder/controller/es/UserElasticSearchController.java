package com.bd.blooddonorfinder.controller.es;

import com.bd.blooddonorfinder.payload.response.ListResponse;
import com.bd.blooddonorfinder.model.es.SearchParam.UserSearchParams;
import com.bd.blooddonorfinder.model.es.documents.DonorSearchDocument;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.service.es.UserElasticSearchService;
import com.bd.blooddonorfinder.utils.Utils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Administrative Elasticsearch maintenance/search endpoints. These expose
 * cross-user data and allow index writes, so they are restricted to admins.
 */
@RestController
@RequestMapping("/es/users")
@Slf4j
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN')")
public class UserElasticSearchController {

    private final UserElasticSearchService userElasticSearchService;

    public UserElasticSearchController(UserElasticSearchService service) {
        this.userElasticSearchService = service;
    }

    @PostMapping("/save")
    public ResponseEntity<ListResponse<DonorSearchDocument>> saveAllUser(
            @Valid @RequestBody DonorSearchDocument user) {
        log.info("saving userdoc to elastic for id={}", user.getId());
        ListResponse<DonorSearchDocument> userList = userElasticSearchService.saveAllUsers(List.of(user));
        return ResponseEntity.ok(userList);
    }

    @GetMapping("/search_by_bloodgroup")
    public ResponseEntity<ListResponse<DonorSearchDocument>> searchByBloodGroup(@RequestParam String bloodGroup) {
        log.info("Searching user for bloodgroup {} in elastic", bloodGroup);
        return ResponseEntity.ok(userElasticSearchService.findByBloodGroup(bloodGroup));
    }

    @PostMapping("/search-user")
    public ResponseEntity<RestApiResponse<ListResponse<DonorSearchDocument>>> searchUser(
            @Valid @RequestBody UserSearchParams searchParams) {
        log.debug("Searching users in elastic");
        ListResponse<DonorSearchDocument> userListResponse;
        RestApiResponse<ListResponse<DonorSearchDocument>> restApiResponse;
        try {
            userListResponse = userElasticSearchService.queryForPage(searchParams);
            log.debug("fetched {} items from elastic", userListResponse.getCount());
            restApiResponse = Utils.buildSuccessRestResponse(HttpStatus.OK, userListResponse);
        } catch (Exception e) {
            restApiResponse = Utils.buildErrorRestResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "PaginatedUserSearch", "Error while searching paginated user data");
            log.error("Error while searching user from elastic", e);
        }
        return ResponseEntity.status(restApiResponse.getStatus()).body(restApiResponse);
    }
}
