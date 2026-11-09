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

    public PiiTokenizer(PiiTokenRepository piiTokenRepository,
                        @Value("${sondhan.pii.encryption-key}") String encryptionKey) {
        this.piiTokenRepository = piiTokenRepository;
        this.encryptionKey = encryptionKey;
    }

    @Transactional
    public PiiToken findOrCreate(String plaintext, PiiType type) {
        String hash = HashUtils.sha256Hex(plaintext);
        return piiTokenRepository.findByValueHash(hash)
                .orElseGet(() -> {
                    logger.debug("Creating new PII token of type {}"+type);
                    return piiTokenRepository.insertEncrypted(
                            type.name(), hash, plaintext, encryptionKey);
                });
    }

    public Optional<PiiToken> findByPlaintext(String plaintext) {
        String hash = HashUtils.sha256Hex(plaintext);
        return piiTokenRepository.findByValueHash(hash);
    }

    public Optional<String> decrypt(UUID tokenId) {
        return piiTokenRepository.decryptValue(tokenId, encryptionKey);
    }

    public boolean exists(String plaintext) {
        return piiTokenRepository.existsByValueHash(HashUtils.sha256Hex(plaintext));
    }

}
