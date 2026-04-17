package com.bd.blooddonorfinder.security.jwt;

import com.bd.blooddonorfinder.model.enums.TokenType;
import com.bd.blooddonorfinder.security.exception.InvalidJwtTokenException;
import com.bd.blooddonorfinder.utils.auth.TokenErrorReason;
import com.bd.blooddonorfinder.utils.constants.SecurityConstants;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class JwtTokenProvider {
    private final RsaKeyProvider rsaKeyProvider;

    @Value("${security.jwt.access.token.validity}")
    private long accessTokenValidity;
    @Value("${security.jwt.refresh.token.validity}")
    private long refreshTokenValidity;

    public JwtTokenProvider(RsaKeyProvider rsaKeyProvider) {
        this.rsaKeyProvider = rsaKeyProvider;
    }

    public String createToken(String username, Long userId, List<String>roles, TokenType tokenType){
        Instant now = Instant.now();
        long tokenValidity = tokenType == TokenType.ACCESS_TOKEN ? accessTokenValidity : refreshTokenValidity;

        Date issuedAt = Date.from(now);
        Date expiration = Date.from(now.plusMillis(TimeUnit.SECONDS.toMillis(tokenValidity)));

        JwtBuilder builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
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

    public Claims parseAndValidate(String token) throws InvalidJwtTokenException {
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
        try {
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
            return bearer.substring(SecurityConstants.TOKEN_PREFIX.length());
        }
        return null;
    }
}
