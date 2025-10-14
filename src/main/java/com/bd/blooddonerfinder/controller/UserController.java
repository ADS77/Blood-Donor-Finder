package com.bd.blooddonerfinder.controller;

import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.model.common.ListResponse;
import com.bd.blooddonerfinder.model.es.SearchParam.UserSearchParams;
import com.bd.blooddonerfinder.model.es.documents.UserSearchDocument;
import com.bd.blooddonerfinder.payload.request.UserRegistrationRequest;
import com.bd.blooddonerfinder.payload.response.RestApiResponse;
import com.bd.blooddonerfinder.repository.UserRepository;
import com.bd.blooddonerfinder.repository.UserSearchRepository;
import com.bd.blooddonerfinder.service.UserService;
import com.bd.blooddonerfinder.util.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/user")
@Slf4j
public class UserController {
    private final UserService userService;
    private final UserRepository userRepository;
    private final UserSearchRepository userSearchRepository;

    public UserController(UserService userService, UserRepository userRepository, UserSearchRepository userSearchRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.userSearchRepository = userSearchRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<RestApiResponse<User>> registerUser(@RequestBody UserRegistrationRequest registrationRequest){
        log.debug("Reg Request : {}", registrationRequest);
        return ResponseEntity.ok().body(userService.registerUser(registrationRequest));
    }

    @PostMapping("/update")
    public ResponseEntity<String> updateUser(User updatedUser){
        log.debug("updating user : {}", updatedUser);
        Optional<User> currentUser = userRepository.findByPhone(updatedUser.getPhone());
        if (currentUser.isPresent()) {
            User existingUser = currentUser.get();
            existingUser.setIsAvailable(updatedUser.getIsAvailable());
            existingUser.setEmail(updatedUser.getEmail());
            existingUser.setPhone(updatedUser.getPhone());
            existingUser.setUpdatedAt(LocalDateTime.now());
            existingUser.setName(updatedUser.getName());
            existingUser.setRole(updatedUser.getRole());
            existingUser.setBloodGroup(updatedUser.getBloodGroup());
            existingUser.setGeoLocation(updatedUser.getGeoLocation());
            existingUser.setLastDonationDate(updatedUser.getLastDonationDate());
            userRepository.save(existingUser);
            return ResponseEntity.ok().body("user updated");
        }
        log.error("User not found");
        return ResponseEntity.ok().body("User not found");
    }

    @PostMapping("/search-user")
    public ResponseEntity<RestApiResponse<ListResponse<UserSearchDocument>>> searchUser(@RequestBody UserSearchParams searchParams){
        log.debug("Searching users in elastic");
        ListResponse<UserSearchDocument> userListResponse;
        RestApiResponse<ListResponse<UserSearchDocument>> restApiResponse;
        try {
            userListResponse = userSearchRepository.queryForPage(searchParams);
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
