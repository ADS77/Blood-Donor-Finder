CREATE TYPE otp_purpose AS ENUM (
    'LOGIN',
    'REGISTER',
    'PASSWORD_RESET'
);

CREATE TABLE otp_codes (
                           id          UUID PRIMARY KEY,
                           user_id     UUID NOT NULL,
                           code_hash   VARCHAR(64) NOT NULL,
                           purpose     otp_purpose NOT NULL,
                           expires_at  TIMESTAMPTZ NOT NULL,
                           used_at     TIMESTAMPTZ,
                           created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                           CONSTRAINT fk_otp_codes_user
                               FOREIGN KEY (user_id)
                                   REFERENCES app_user(id)
                                   ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_otp_active
    ON otp_codes(user_id, purpose)
    WHERE used_at IS NULL;