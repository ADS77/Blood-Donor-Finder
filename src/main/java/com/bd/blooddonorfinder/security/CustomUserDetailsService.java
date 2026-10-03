package com.bd.blooddonorfinder.security;

import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        UUID uuid;
        try {
            uuid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new UsernameNotFoundException("Invalid user ID: " + userId);
        }
        User user = userRepository.findUserWithRolesAndPermissionsById(uuid)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));

        return buildPrincipal(user);
    }

    public UserPrincipal buildPrincipal(User user) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        if(user.getRoles() != null){
            user.getRoles().forEach(role -> {
                String authority = role.getName().startsWith(AppPermissions.ROLE_PREFIX)
                        ? role.getName()
                        : AppPermissions.ROLE_PREFIX + role.getName();
                authorities.add(new SimpleGrantedAuthority(authority));
                if(role.getPermissions() != null){
                    role.getPermissions().forEach(permission ->
                            authorities.add(new SimpleGrantedAuthority(permission.getName())));
                }
            });
        }
        return new UserPrincipal(
                user.getId(),
                user.getFirstName(),
                user.getPassword(),
                user.isEnabled(),
                authorities
        );
    }
}
