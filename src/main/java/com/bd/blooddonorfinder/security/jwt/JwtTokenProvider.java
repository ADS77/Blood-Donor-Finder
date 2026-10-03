package com.bd.blooddonorfinder.security.jwt;

import com.bd.blooddonorfinder.model.enums.TokenType;
import com.bd.blooddonorfinder.payload.response.TokenResponse;
import com.bd.blooddonorfinder.security.UserPrincipal;
import com.bd.blooddonorfinder.security.exception.InvalidJwtTokenException;
import com.bd.blooddonorfinder.service.auth.TokenStorageService;
import com.bd.blooddonorfinder.utils.constants.SecurityConstants;
import com.bd.blooddonorfinder.utils.constants.TokenErrorReason;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
public class JwtTokenProvider {
    private final RsaKeyProvider rsaKeyProvider;
    private final TokenStorageService tokenStorageService;

    @Value("${security.jwt.access.token.validity}")
    private long accessTokenValidity;
    @Value("${security.jwt.refresh.token.validity}")
    private long refreshTokenValidity;

    public JwtTokenProvider(RsaKeyProvider rsaKeyProvider, TokenStorageService tokenStorageService) {
        this.rsaKeyProvider = rsaKeyProvider;
        this.tokenStorageService = tokenStorageService;
    }

    public String createToken(String username, UUID userId, List<String>roles, TokenType tokenType){
        Instant now = Instant.now();
        long tokenValidity = tokenType == TokenType.ACCESS_TOKEN ? accessTokenValidity : refreshTokenValidity;

        Date issuedAt = Date.from(now);
        Date expiration = Date.from(now.plusMillis(TimeUnit.SECONDS.toMillis(tokenValidity)));

        JwtBuilder builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim(SecurityConstants.CLAIM_USER_ID, userId)
                .claim(SecurityConstants.TOKEN_TYPE, tokenType.getValue())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(rsaKeyProvider.getPrivateKey(), Jwts.SIG.RS256);

        if(tokenType == tokenType.ACCESS_TOKEN && roles != null){
            builder.claim(SecurityConstants.CLAIM_ROLES, roles);
        }
        return builder.compact();
    }

    public Claims parseAndValidateClaims(String token) throws InvalidJwtTokenException {
        try {
            return Jwts.parser()
                    .verifyWith(rsaKeyProvider.getPublicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        }catch (ExpiredJwtException e) {
            throw new InvalidJwtTokenException("Token has expired", e, TokenErrorReason.EXPIRED);
        } catch (SignatureException e) {
            throw new InvalidJwtTokenException("Invalid token signature", e, TokenErrorReason.SIGNATURE_INVALID);
        } catch (MalformedJwtException | IllegalArgumentException e) {
            throw new InvalidJwtTokenException("Malformed token", e, TokenErrorReason.MALFORMED);
        }
    }

    public Claims parseExpiredToken(String token) throws InvalidJwtTokenException {
        try{
            return Jwts.parser()
                    .verifyWith(rsaKeyProvider.getPublicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        } catch (Exception e) {
            throw new InvalidJwtTokenException("Cannot parse token", e, TokenErrorReason.MALFORMED);
        }
    }

    public String getUsername(Claims claims) {
        return claims.getSubject();
    }

    public String getTokenId(Claims claims) {
        return claims.getId();
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        List<String> roles = claims.get(SecurityConstants.CLAIM_ROLES, List.class);
        return roles != null ? roles : Collections.emptyList();
    }

    public TokenType getTokenType(Claims claims) {
        String type = claims.get(SecurityConstants.TOKEN_TYPE, String.class);
        return TokenType.valueOf(type);
    }

    public long getAccessTokenValiditySeconds() {
        return accessTokenValidity ;
    }

    public String resolveToken( HttpServletRequest request) {
        String bearer = request.getHeader(SecurityConstants.TOKEN_HEADER);
        if (bearer != null && bearer.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            return bearer.substring(SecurityConstants.TOKEN_PREFIX.length()).trim();
        }
        return null;
    }

    public UUID getUserId(Claims claims) {
        Object uid = claims.get(SecurityConstants.CLAIM_USER_ID);
        if (uid instanceof UUID uuid) {
            return uuid;
        }
        return null;
    }

    public Object maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "[EMPTY_TOKEN]";
        }
        if (token.length() <= 12) {
            return "[PROTECTED_TOKEN]";
        }
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }

    private TokenResponse issueTokenPair(String username, UUID userId, List<String> roles) throws InvalidJwtTokenException {
        String accessToken = issueAccessToken(username, userId,roles);
        String refreshToken = issueRefreshToken(username, userId, roles);
        return TokenResponse.of(
                accessToken,
                refreshToken,
                accessTokenValidity,
                username,
                roles);
    }

    private String issueAccessToken(String username, UUID userId, List<String> roles) throws InvalidJwtTokenException {
        String accessToken = createToken(username, userId, roles, TokenType.ACCESS_TOKEN);
        Claims accessClaims = parseAndValidateClaims(accessToken);
        long accessTtl = TimeUnit.SECONDS.toMillis(accessTokenValidity);
        tokenStorageService.whitelistAccessToken(accessClaims.getId(), username, accessTtl);
        return accessToken;
    }

    private String issueRefreshToken(String username, UUID userId, List<String> roles) throws InvalidJwtTokenException {
        String refreshToken = createToken(username, userId, roles, TokenType.REFRESH_TOKEN);
        Claims accessClaims = parseAndValidateClaims(refreshToken);
        long refreshTtl = TimeUnit.SECONDS.toMillis(refreshTokenValidity);
        tokenStorageService.whitelistRefreshToken(accessClaims.getId(), username, refreshTtl);
        return refreshToken;
    }


    public TokenResponse getTokenPair(UserPrincipal principal) throws InvalidJwtTokenException {

        return issueTokenPair(principal.getUsername(), principal.getId(),principal.getAuthorityStrings());
    }

    public String getAccessToken(UserPrincipal principal) throws InvalidJwtTokenException {
        return issueAccessToken(principal.getUsername(), principal.getId(),principal.getAuthorityStrings());
    }

    public String getRefreshToken(UserPrincipal principal) throws InvalidJwtTokenException {
        return issueRefreshToken(principal.getUsername(), principal.getId(),principal.getAuthorityStrings());
    }
}
