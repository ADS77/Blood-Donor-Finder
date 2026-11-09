
CREATE TYPE pii_type AS ENUM ('PHONE', 'EMAIL');

CREATE TABLE pii_tokens (
                            id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            pii_type        pii_type NOT NULL,
                            encrypted_value BYTEA NOT NULL,
                            value_hash      VARCHAR(64) NOT NULL,
                            created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Lookup by hash
CREATE UNIQUE INDEX uq_pii_type_value_hash
    ON pii_tokens (pii_type, value_hash);


ALTER TABLE app_user
    ADD CONSTRAINT fk_users_phone_token
        FOREIGN KEY (phone_token_id)
            REFERENCES pii_tokens(id)
            ON DELETE SET NULL;

ALTER TABLE app_user
    ADD CONSTRAINT fk_users_email_token
        FOREIGN KEY (email_token_id)
            REFERENCES pii_tokens(id)
            ON DELETE SET NULL;