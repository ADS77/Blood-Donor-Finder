package com.bd.blooddonorfinder.security.jwt;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

@Component
@Slf4j
public class RsaKeyProvider {

    @Value("${rsa.private.key.cert.path}")
    private String privateKeyFilePath;
    @Value("${rsa.public.key.cert.path}")
    private String publicKeyFilePath;
    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    void loadKeys() {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");

            byte[] privBytes = Files.readAllBytes(
                    new ClassPathResource(privateKeyFilePath).getFile().toPath());
            this.privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));

            byte[] pubBytes = Files.readAllBytes(
                    new ClassPathResource(publicKeyFilePath).getFile().toPath());
            this.publicKey = kf.generatePublic(new X509EncodedKeySpec(pubBytes));

            log.info("RSA key pair loaded successfully (algorithm={})", privateKey.getAlgorithm());
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to load RSA keys — server cannot start", e);
        }
    }

    public PrivateKey getPrivateKey(){
        return privateKey;
    }
    public PublicKey getPublicKey(){
        return publicKey;
    }
}
