/*-- ============================================================
-- Migrate app_user.id from BIGINT to UUID
--
-- Development migration:
-- Existing app_user and user_roles data will be discarded.
-- ============================================================


-- ============================================================
-- 1. Drop dependent user_roles table
-- ============================================================

DROP TABLE IF EXISTS user_roles;


-- ============================================================
-- 2. Drop app_user
-- ============================================================

DROP TABLE IF EXISTS app_user;

-- ============================================================
-- 3. Recreate app_user with UUID primary key
-- ============================================================

CREATE TABLE app_user (
                          id UUID PRIMARY KEY,

                          first_name VARCHAR(100) NOT NULL,
                          last_name VARCHAR(100) NOT NULL,

                          phone_token_id UUID,
                          email_token_id UUID,

                          password VARCHAR(255) NOT NULL,

                          blood_group VARCHAR(20),

                          verified BOOLEAN,

                          is_active BOOLEAN NOT NULL DEFAULT TRUE,

                          failed_otp_attempts INTEGER NOT NULL DEFAULT 0,

                          locked_until TIMESTAMP WITH TIME ZONE,

                          is_available BOOLEAN,

                          last_donation_date TIMESTAMP,

                          rating DOUBLE PRECISION DEFAULT 0.0,

                          total_donations BIGINT DEFAULT 0,

                          image_url VARCHAR(255),

    -- ========================================================
    -- GeoLocation
    -- ========================================================

                          address VARCHAR(500),

                          city VARCHAR(100) NOT NULL,

                          district VARCHAR(100),

                          country VARCHAR(100),

                          latitude DECIMAL(10,6),

                          longitude DECIMAL(10,6),

                          zipcode VARCHAR(20),

                          geo_status VARCHAR(50) DEFAULT 'PENDING',

                          geo_retry_count INTEGER DEFAULT 0,

                          geo_last_error VARCHAR(255),

                          geo_claimed_at TIMESTAMP WITH TIME ZONE,

    -- ========================================================
    -- Audit / versioning
    -- ========================================================

                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                          version BIGINT,

                          enabled BOOLEAN NOT NULL DEFAULT TRUE,


    -- ========================================================
    -- Foreign keys
    -- ========================================================

                          CONSTRAINT fk_app_user_phone_token
                              FOREIGN KEY (phone_token_id)
                                  REFERENCES pii_tokens(id),

                          CONSTRAINT fk_app_user_email_token
                              FOREIGN KEY (email_token_id)
                                  REFERENCES pii_tokens(id)
);


-- ============================================================
-- 4. Recreate indexes for app_user
-- ============================================================

/*CREATE INDEX idx_app_user_email
    ON app_user(email_token_id);

CREATE INDEX idx_app_user_phone
    ON app_user(phone_token_id);*/

CREATE INDEX idx_app_user_blood_group
    ON app_user(blood_group);

CREATE INDEX idx_app_user_is_available
    ON app_user(is_available)
    WHERE is_available = TRUE;

CREATE INDEX idx_app_user_city
    ON app_user(city);

CREATE INDEX idx_app_user_geo_status
    ON app_user(geo_status);


-- ============================================================
-- 5. Recreate user_roles with UUID user_id
-- ============================================================

CREATE TABLE user_roles (
                            user_id UUID NOT NULL,
                            role_id BIGINT NOT NULL,

                            PRIMARY KEY (user_id, role_id),

                            CONSTRAINT fk_user_roles_user
                                FOREIGN KEY (user_id)
                                    REFERENCES app_user(id)
                                    ON DELETE CASCADE,

                            CONSTRAINT fk_user_roles_role
                                FOREIGN KEY (role_id)
                                    REFERENCES roles(id)
                                    ON DELETE RESTRICT
);


-- ============================================================
-- 6. Recreate user_roles indexes
-- ============================================================

CREATE INDEX idx_user_roles_user_id
    ON user_roles(user_id);

CREATE INDEX idx_user_roles_role_id
    ON user_roles(role_id);*/