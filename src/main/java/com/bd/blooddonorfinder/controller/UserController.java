package com.bd.blooddonorfinder.controller;

import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.repository.UserRepository;
import com.bd.blooddonorfinder.service.UserService;
import lombok.extern.slf4j.Slf4j;
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

    public UserController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }


    @PostMapping("/update")
    public ResponseEntity<String> updateUser(@RequestBody User updatedUser){
        log.debug("updating user : {}", updatedUser);
        Optional<User> currentUser = userRepository.findByPhoneToken(updatedUser.getPhoneToken());
        if (currentUser.isPresent()) {
            User existingUser = currentUser.get();
            existingUser.setIsAvailable(updatedUser.getIsAvailable());
            existingUser.setFirstName(updatedUser.getFirstName());
            existingUser.setLastName(updatedUser.getLastName());
            existingUser.setUpdatedAt(LocalDateTime.now());
            existingUser.setRoles(updatedUser.getRoles());
            existingUser.setBloodGroup(updatedUser.getBloodGroup());
            existingUser.setGeoLocation(updatedUser.getGeoLocation());
            existingUser.setLastDonationDate(updatedUser.getLastDonationDate());
            userRepository.save(existingUser);
            return ResponseEntity.ok().body("user updated");
        }
        log.error("User not found");
        return ResponseEntity.ok().body("User not found");
    }


}
