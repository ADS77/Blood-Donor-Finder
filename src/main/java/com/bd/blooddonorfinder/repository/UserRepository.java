package com.bd.blooddonorfinder.repository;

import com.bd.blooddonorfinder.model.User;
import com.bd.blooddonorfinder.model.enums.BloodGroup;
import com.bd.blooddonorfinder.model.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User>findByName(String username);
    Optional<User> findById(long id);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    List<User> findNearByAndBloodGroupAndGeoLocationCity(BloodGroup bloodGroup, String city);

}
