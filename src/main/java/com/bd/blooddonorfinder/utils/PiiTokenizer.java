package com.bd.blooddonorfinder.utils;

import com.bd.blooddonorfinder.model.PiiToken;
import com.bd.blooddonorfinder.model.enums.PiiType;
import com.bd.blooddonorfinder.repository.auth.PiiTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
public class PiiTokenizer {
    public static final Logger logger = LoggerFactory.getLogger(PiiTokenizer.class);

    private final PiiTokenRepository piiTokenRepository;
    private final String encryptionKey;
    private final String hashKey;

    public PiiTokenizer(PiiTokenRepository piiTokenRepository,
                        @Value("${sondhan.pii.encryption-key}") String encryptionKey,
                        @Value("${sondhan.pii.hash-key}") String hashKey) {
        this.piiTokenRepository = piiTokenRepository;
        this.encryptionKey = encryptionKey;
        this.hashKey = hashKey;
    }

    @Transactional
    public PiiToken findOrCreate(String plaintext, PiiType type) {
        return findByPlaintext(plaintext)
                .orElseGet(() -> {
                    logger.debug("Creating new PII token of type {}", type);
                    return piiTokenRepository.insertEncrypted(
                            type.name(), hash(plaintext), plaintext, encryptionKey);
                });
    }

    /**
     * Looks up a PII token using the keyed hash. The legacy unkeyed SHA-256
     * hash is still accepted so that accounts created before the keyed hash
     * was introduced continue to work; new values always use the keyed hash.
     */
    public Optional<PiiToken> findByPlaintext(String plaintext) {
        Optional<PiiToken> current = piiTokenRepository.findByValueHash(hash(plaintext));
        if (current.isPresent()) {
            return current;
        }
        return piiTokenRepository.findByValueHash(legacyHash(plaintext));
    }

    public Optional<String> decrypt(UUID tokenId) {
        return piiTokenRepository.decryptValue(tokenId, encryptionKey);
    }

    public boolean exists(String plaintext) {
        return findByPlaintext(plaintext).isPresent();
    }

    private String hash(String plaintext) {
        return HashUtils.hmacSha256Hex(hashKey, plaintext);
    }

    private String legacyHash(String plaintext) {
        return HashUtils.sha256Hex(plaintext);
    }
}
