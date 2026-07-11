package com.bd.blooddonorfinder.service.auth;

import com.bd.blooddonorfinder.model.User;
import com.bd.blooddonorfinder.model.enums.Role;
import com.bd.blooddonorfinder.model.enums.TokenType;
import com.bd.blooddonorfinder.payload.response.TokenResponse;
import com.bd.blooddonorfinder.repository.UserRepository;
import com.bd.blooddonorfinder.security.exception.InvalidJwtTokenException;
import com.bd.blooddonorfinder.security.jwt.JwtTokenProvider;
import com.bd.blooddonorfinder.utils.auth.TokenErrorReason;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AuthenticationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final TokenStorageService tokenStorageService;

    public AuthenticationService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 JwtTokenProvider tokenProvider,
                                 TokenStorageService tokenStorageService){
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.tokenStorageService = tokenStorageService;
    }

    @Value("${security.jwt.access.token.validity}")
    private long accessTokenValidity;
    @Value("${security.jwt.refresh.token.validity}")
    private long refreshTokenValidity;

    public TokenResponse login(String username, String password) throws InvalidJwtTokenException {
        User user = userRepository.findByName(username.trim().toLowerCase())
                .orElseThrow(()-> new UsernameNotFoundException("User not found : "+username));

        boolean validUser = user != null && passwordEncoder.matches(password, user.getPassword());

        if(!validUser){
            throw new BadCredentialsException("Credentials not matched");
        }

        List<String> roles = resolveRoles(user);
        return  issueTokenPair(user.getName(), user.getId(), roles);
    }

    public void logout(String accessToken, String refreshToken) {
        revokeToken(accessToken);
        if (refreshToken != null) {
            revokeToken(refreshToken);
        }
    }

    public TokenResponse refresh(String refreshTokenString) throws InvalidJwtTokenException {
        Claims claims = tokenProvider.parseAndValidate(refreshTokenString);

        TokenType type = tokenProvider.getTokenType(claims);
        if (type != TokenType.REFRESH_TOKEN) {
            throw new InvalidJwtTokenException("Expected refresh token", TokenErrorReason.WRONG_TYPE);
        }

        // Verify it hasn't been revoked
        String jti = tokenProvider.getTokenId(claims);
        if (!tokenStorageService.isRefreshTokenWhitelisted(jti)) {
            log.warn("Attempted reuse of revoked refresh token jti={}", jti);
            // Potential token theft — revoke ALL tokens for this user
            String username = tokenProvider.getUsername(claims);
            tokenStorageService.revokeAllTokensForUser(username);
            throw new InvalidJwtTokenException("Refresh token has been revoked", TokenErrorReason.REVOKED);
        }

        // Revoke old refresh token (rotation)
        tokenStorageService.revokeRefreshToken(jti);

        // Reload user and issue new pair
        String username = tokenProvider.getUsername(claims);
        User user = userRepository.findByName(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<String> roles = resolveRoles(user);
        return issueTokenPair(user.getName(), user.getId(), roles);
    }

    public void forceLogoutUser(String username) {
        tokenStorageService.revokeAllTokensForUser(username);
        log.info("Force-logged out user={}", username);
    }


    private void revokeToken(String tokenString) {
        try {
            Claims claims = tokenProvider.parseExpiredToken(tokenString);
            String jti    = tokenProvider.getTokenId(claims);
            TokenType type = tokenProvider.getTokenType(claims);

            if (type == TokenType.ACCESS_TOKEN) {
                tokenStorageService.revokeAccessToken(jti);
            } else {
                tokenStorageService.revokeRefreshToken(jti);
            }
        } catch (InvalidJwtTokenException e) {
            log.debug("Could not parse token during revocation — may already be invalid: {}", e.getMessage());
        }
    }

    private TokenResponse issueTokenPair(String username, Long userId, List<String> roles) throws InvalidJwtTokenException {
        String accessToken = tokenProvider.createToken(username, userId, roles, TokenType.ACCESS_TOKEN);
        String refreshToken = tokenProvider.createToken(username, userId, roles, TokenType.REFRESH_TOKEN);

        Claims accessClaims  = tokenProvider.parseAndValidate(accessToken);
        Claims refreshClaims = tokenProvider.parseAndValidate(refreshToken);

        long accessTtl  = TimeUnit.SECONDS.toMillis(accessTokenValidity);
        long refreshTtl = TimeUnit.SECONDS.toMillis(refreshTokenValidity);

        tokenStorageService.whitelistAccessToken(accessClaims.getId(), username, accessTtl);
        tokenStorageService.whitelistRefreshToken(refreshClaims.getId(), username, refreshTtl);
        return TokenResponse.of(
                accessToken,
                refreshToken,
                tokenProvider.getAccessTokenValiditySeconds(),
                username,
                roles);

    }

    // To-DO : need to adopt User entity's role model.
    private List<String> resolveRoles(User user) {
        return Optional.ofNullable(user.getRoles())
                .orElse(Collections.emptySet())
                .stream()
                .map(Role::name)
                .toList();
    }
}
