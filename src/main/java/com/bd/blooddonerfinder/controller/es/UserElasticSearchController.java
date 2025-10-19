package com.bd.blooddonerfinder.controller.es;

import com.bd.blooddonerfinder.model.common.ListResponse;
import com.bd.blooddonerfinder.model.es.SearchParam.UserSearchParams;
import com.bd.blooddonerfinder.model.es.documents.UserSearchDocument;
import com.bd.blooddonerfinder.payload.response.RestApiResponse;
import com.bd.blooddonerfinder.service.es.UserElasticSearchService;
import com.bd.blooddonerfinder.util.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/es/users")
@Slf4j
public class UserElasticSearchController {

    private final UserElasticSearchService userElasticSearchService;
    public UserElasticSearchController(UserElasticSearchService service){
        this.userElasticSearchService = service;
    }

    @PostMapping("/save")
    public ResponseEntity<ListResponse<UserSearchDocument>> saveAllUser(@RequestBody UserSearchDocument user){
        log.info("saving userdoc to elastic : {}", user);
        ListResponse<UserSearchDocument> userList = userElasticSearchService.saveAllUsers(List.of(user));
        return ResponseEntity.ok(userList);
    }

    @GetMapping("/search_by_bloodgroup")
    public ResponseEntity<ListResponse<UserSearchDocument>> searchByBloodGroup(@RequestParam String bloodGroup){
        log.info("Searching user for bloodgroup {} in elastic", bloodGroup);
        return ResponseEntity.ok(userElasticSearchService.findByBloodGroup(bloodGroup));
    }

    @PostMapping("/search-user")
    public ResponseEntity<RestApiResponse<ListResponse<UserSearchDocument>>> searchUser(@RequestBody UserSearchParams searchParams){
        log.debug("Searching users in elastic");
        ListResponse<UserSearchDocument> userListResponse;
        RestApiResponse<ListResponse<UserSearchDocument>> restApiResponse;
        try {
            userListResponse = userElasticSearchService.queryForPage(searchParams);
            log.debug("fetched {} items from elastic", userListResponse.getCount());
            restApiResponse = Utils.buildSuccessRestResponse(HttpStatus.OK,userListResponse);
        }
        catch (Exception e){
            restApiResponse = Utils.buildErrorRestResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "PaginatedUserSearch", "Error while searching paginated user data");
            log.error("Error while searching user from elastic");
        }
        return ResponseEntity.status(restApiResponse.getStatus()).body(restApiResponse);
    }
}
