package com.bd.blooddonorfinder.utils;

import com.bd.blooddonorfinder.exception.EmailPayloadException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Authenticated encryption for email notification payloads.
 *
 * <p>Encrypting the payload before it enters the outbox keeps OTP codes and
 * recipient addresses out of plaintext database rows, Kafka messages and
 * dead-letter topics. The key is derived from a dedicated configuration
 * property with a domain separator, so it is independent from the pgcrypto key
 * even when the default fallback is used.</p>
 *
 * <p>The wire format is {@code version || 12-byte IV || ciphertext+tag},
 * Base64 encoded. The version byte leaves room for a future key or algorithm
 * rotation.</p>
 */
@Component
public class EmailPayloadCipher {

    private static final byte FORMAT_VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public EmailPayloadCipher(
            @Value("${sondhan.email.payload-key:${sondhan.pii.encryption-key}}") String keyMaterial) {
        if (keyMaterial == null || keyMaterial.isBlank()) {
            throw new IllegalStateException(
                    "sondhan.email.payload-key (or sondhan.pii.encryption-key) must be configured");
        }
        this.key = new SecretKeySpec(deriveKey(keyMaterial), "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new EmailPayloadException("Cannot encrypt a null email payload");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(1 + IV_LENGTH + ciphertext.length);
            buffer.put(FORMAT_VERSION).put(iv).put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new EmailPayloadException("Failed to encrypt email payload", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new EmailPayloadException("Cannot decrypt an empty email payload");
        }
        try {
            byte[] data = Base64.getDecoder().decode(encoded);
            if (data.length <= 1 + IV_LENGTH || data[0] != FORMAT_VERSION) {
                throw new EmailPayloadException("Unsupported email payload format");
            }
            byte[] iv = Arrays.copyOfRange(data, 1, 1 + IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(data, 1 + IV_LENGTH, data.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new EmailPayloadException("Failed to decrypt email payload", e);
        }
    }

    private static byte[] deriveKey(String keyMaterial) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(("sondhan-email-outbox:" + keyMaterial)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
