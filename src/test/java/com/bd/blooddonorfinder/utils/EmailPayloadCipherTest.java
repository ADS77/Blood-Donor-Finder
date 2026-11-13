package com.bd.blooddonorfinder.utils;

import com.bd.blooddonorfinder.exception.EmailPayloadException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailPayloadCipherTest {

    private final EmailPayloadCipher cipher = new EmailPayloadCipher("unit-test-key");

    @Test
    void roundTrip_returnsOriginalPlaintext() {
        String plaintext = "{\"mailTo\":[\"donor@example.com\"],\"body\":\"123456\"}";

        String encrypted = cipher.encrypt(plaintext);

        assertNotEquals(plaintext, encrypted);
        assertEquals(plaintext, cipher.decrypt(encrypted));
    }

    @Test
    void encrypt_usesRandomIvSoCiphertextsDiffer() {
        String plaintext = "same message";

        String first = cipher.encrypt(plaintext);
        String second = cipher.encrypt(plaintext);

        assertNotEquals(first, second);
        assertEquals(plaintext, cipher.decrypt(first));
        assertEquals(plaintext, cipher.decrypt(second));
    }

    @Test
    void decrypt_rejectsTamperedCiphertext() {
        byte[] data = Base64.getDecoder().decode(cipher.encrypt("sensitive"));
        data[data.length - 1] ^= 0x01;

        assertThrows(EmailPayloadException.class,
                () -> cipher.decrypt(Base64.getEncoder().encodeToString(data)));
    }

    @Test
    void decrypt_rejectsMalformedInput() {
        assertThrows(EmailPayloadException.class, () -> cipher.decrypt("not-base64"));
        assertThrows(EmailPayloadException.class, () -> cipher.decrypt(null));
    }

    @Test
    void constructor_rejectsBlankKey() {
        assertThrows(IllegalStateException.class, () -> new EmailPayloadCipher("  "));
    }
}
