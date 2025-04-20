package com.bd.blooddonerfinder.controller;

import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.payload.request.UserRegistrationRequest;
import com.bd.blooddonerfinder.payload.response.RestApiResponse;
import com.bd.blooddonerfinder.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public ResponseEntity<RestApiResponse<User>> registerUser(@RequestBody UserRegistrationRequest registrationRequest){
        return ResponseEntity.ok().body(userService.registerUser(registrationRequest));
    }
}
