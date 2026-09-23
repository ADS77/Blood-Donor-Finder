package com.bd.blooddonorfinder.model;

import com.bd.blooddonorfinder.model.enums.PiiType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pii_tokens")
public class PiiToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "pii_type", nullable = false, updatable = false)
    private PiiType piiType;

    @Column(name = "value_hash", nullable = false, unique = true, updatable = false, length = 64)
    private String valueHash;

    @Column(name = "encrypted_value", nullable = false)
    private byte[] encryptedValue;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected PiiToken() {}

    public PiiToken(PiiType piiType, String valueHash, byte[] encryptedValue) {
        this.piiType = piiType;
        this.valueHash = valueHash;
        this.encryptedValue = encryptedValue;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public PiiType getPiiType() {
        return piiType;
    }

    public String getValueHash() {
        return valueHash;
    }

    public byte[] getEncryptedValue() {
        return encryptedValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
