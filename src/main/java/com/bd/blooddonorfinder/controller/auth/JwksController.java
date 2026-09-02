package com.bd.blooddonorfinder.controller.auth;

import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.security.jwt.JwtTokenProvider;
import com.bd.blooddonorfinder.security.jwt.RsaKeyProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "JWKS", description = "Public key endpoint for token verification")
public class JwksController {

    Logger logger = LoggerFactory.getLogger(JwksController.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final RsaKeyProvider rsaKeyProvider;

    public JwksController(JwtTokenProvider jwtTokenProvider,
                          RsaKeyProvider rsaKeyProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.rsaKeyProvider = rsaKeyProvider;
    }

    @Operation(
            summary = "JSON Web Key Set",
            description = "Returns the RSA public key in JWK Set format for RS256 token verification")
    @GetMapping("/jwks.json")
    public ResponseEntity<RestApiResponse<Map<String, Object>>> jwks() {
        RSAPublicKey publicKey = (RSAPublicKey) rsaKeyProvider.getPublicKey();
        if (publicKey instanceof RSAPublicKey ) {
            logger.debug("Modulus: " + publicKey.getModulus());
            logger.debug("Exponent: " + publicKey.getPublicExponent());
        } else {
            throw new IllegalArgumentException(
                    "The provided public key is not an RSA public key"
            );
        }
        Map<String, Object> jwk = Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "n", base64UrlEncode(publicKey.getModulus()),
                "e", base64UrlEncode(publicKey.getPublicExponent()));

        Map<String, Object> jwkSet = Map.of("keys", List.of(jwk));

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(RestApiResponse.of(jwkSet));
    }

    private String base64UrlEncode(BigInteger value) {
        byte[] bytes = value.toByteArray();
        // Remove leading zero byte that BigInteger may prepend for sign
        if (bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
