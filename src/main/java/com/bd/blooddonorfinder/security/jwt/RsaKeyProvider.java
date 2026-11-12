package com.bd.blooddonorfinder.security.jwt;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

@Component
@Slf4j
public class RsaKeyProvider {

    private static final int MIN_KEY_BITS = 2048;

    @Value("${rsa.private.key.path:}")
    private String privateKeyPath;
    @Value("${rsa.public.key.path:}")
    private String publicKeyPath;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    void loadKeys() {
        try {
            if (!StringUtils.hasText(privateKeyPath) && !StringUtils.hasText(publicKeyPath)) {
                // Development / single-instance fallback. Production deployments
                // must mount a stable key pair so tokens survive restarts and
                // are verifiable by every instance.
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(MIN_KEY_BITS);
                KeyPair keyPair = generator.generateKeyPair();
                this.privateKey = keyPair.getPrivate();
                this.publicKey = keyPair.getPublic();
                log.warn("No RSA key paths configured. Generated an ephemeral {} bit key pair; "
                        + "tokens will be invalid after restart and across instances. "
                        + "Set RSA_PRIVATE_KEY_PATH and RSA_PUBLIC_KEY_PATH in production.",
                        MIN_KEY_BITS);
                return;
            }

            if (!StringUtils.hasText(privateKeyPath) || !StringUtils.hasText(publicKeyPath)) {
                throw new IllegalStateException(
                        "Both RSA_PRIVATE_KEY_PATH and RSA_PUBLIC_KEY_PATH must be configured");
            }

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            this.privateKey = keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(Files.readAllBytes(Path.of(privateKeyPath))));
            this.publicKey = keyFactory.generatePublic(
                    new X509EncodedKeySpec(Files.readAllBytes(Path.of(publicKeyPath))));

            validateKeyPair();
            log.info("RSA key pair loaded successfully (algorithm={}, bits={})",
                    privateKey.getAlgorithm(), ((RSAPrivateKey) privateKey).getModulus().bitLength());
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to load RSA keys - server cannot start", e);
        }
    }

    private void validateKeyPair() {
        if (!(privateKey instanceof RSAPrivateCrtKey rsaPrivate)) {
            throw new IllegalStateException("Private key must be an RSA PKCS#8 private key");
        }
        if (!(publicKey instanceof RSAPublicKey rsaPublic)) {
            throw new IllegalStateException("Public key must be an RSA X.509 public key");
        }
        if (rsaPrivate.getModulus().bitLength() < MIN_KEY_BITS) {
            throw new IllegalStateException("RSA key must be at least " + MIN_KEY_BITS + " bits");
        }
        if (!rsaPrivate.getModulus().equals(rsaPublic.getModulus())) {
            throw new IllegalStateException("RSA private and public keys do not belong to the same key pair");
        }
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }
}
