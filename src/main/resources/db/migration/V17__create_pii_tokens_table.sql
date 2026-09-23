CREATE TYPE pii_type AS ENUM ('PHONE', 'EMAIL');

CREATE TABLE pii_tokens (
                            id              UUID        PRIMARY KEY DEFAULT,
                            pii_type        pii_type    NOT NULL,
                            encrypted_value BYTEA       NOT NULL,
                            value_hash      VARCHAR (64)    NOT NULL,
                            created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Lookup by hash (e.g. "does this phone already exist?")
CREATE UNIQUE INDEX uq_pii_value_hash ON pii_tokens (value_hash);

-- Now add the FK constraints to app_user table
ALTER TABLE app_user
    ADD CONSTRAINT fk_users_phone_token
        FOREIGN KEY (phone_token_id) REFERENCES pii_tokens(id) ON DELETE SET NULL;

ALTER TABLE app_user
    ADD CONSTRAINT fk_users_email_token
        FOREIGN KEY (email_token_id) REFERENCES pii_tokens(id) ON DELETE SET NULL;
