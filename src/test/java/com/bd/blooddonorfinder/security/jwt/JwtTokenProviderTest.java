package com.bd.blooddonorfinder.security.jwt;

import com.bd.blooddonorfinder.model.enums.TokenType;
import com.bd.blooddonorfinder.service.auth.TokenStorageService;
import com.bd.blooddonorfinder.utils.constants.SecurityConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for the token-type claim mismatch: tokens are signed with the
 * lowercase claim value ("access_token") and the parser must resolve it back to
 * the enum constant. A raw TokenType.valueOf on the claim value rejected every
 * freshly issued token at the authentication filter.
 */
class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private TokenStorageService tokenStorageService;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        RsaKeyProvider rsaKeyProvider = mock(RsaKeyProvider.class);
        when(rsaKeyProvider.getPrivateKey()).thenReturn(keyPair.getPrivate());
        when(rsaKeyProvider.getPublicKey()).thenReturn(keyPair.getPublic());

        tokenStorageService = mock(TokenStorageService.class);

        tokenProvider = new JwtTokenProvider(rsaKeyProvider, tokenStorageService);
        ReflectionTestUtils.setField(tokenProvider, "accessTokenValidity", 1200L);
        ReflectionTestUtils.setField(tokenProvider, "refreshTokenValidity", 604800L);
        ReflectionTestUtils.setField(tokenProvider, "issuer", "sondhan");
    }

    @Test
    void issuedTokenTypeClaimResolvesToEnumConstant() throws Exception {
        String token = tokenProvider.createToken("Smoke", UUID.randomUUID(),
                List.of("DONOR"), TokenType.ACCESS_TOKEN);

        Claims claims = tokenProvider.parseAndValidateClaims(token);

        assertEquals(TokenType.ACCESS_TOKEN, tokenProvider.getTokenType(claims));
        assertEquals("access_token", claims.get(SecurityConstants.TOKEN_TYPE, String.class));
    }

    @Test
    void refreshTokenClaimResolvesToEnumConstant() throws Exception {
        String token = tokenProvider.createToken("Smoke", UUID.randomUUID(),
                List.of("DONOR"), TokenType.REFRESH_TOKEN);

        Claims claims = tokenProvider.parseAndValidateClaims(token);

        assertEquals(TokenType.REFRESH_TOKEN, tokenProvider.getTokenType(claims));
    }

    @Test
    void unsupportedTokenTypeClaimIsRejected() {
        Claims claims = Jwts.claims()
                .add(SecurityConstants.TOKEN_TYPE, "magic_token")
                .build();

        assertThrows(IllegalArgumentException.class, () -> tokenProvider.getTokenType(claims));
    }
}
