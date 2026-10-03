CREATE TABLE app_user (
                          id UUID PRIMARY KEY,

                          first_name VARCHAR(100) NOT NULL,
                          last_name VARCHAR(100) NOT NULL,

                          phone_token_id UUID,
                          email_token_id UUID,

                          password VARCHAR(255) NOT NULL,

                          blood_group VARCHAR(20),
                          is_verified BOOLEAN,
                          is_active BOOLEAN NOT NULL DEFAULT TRUE,

                          failed_otp_attempts INTEGER NOT NULL DEFAULT 0,
                          locked_until TIMESTAMP WITH TIME ZONE,

                          is_available BOOLEAN,
                          last_donation_date TIMESTAMP,

                          rating DOUBLE PRECISION DEFAULT 0.0,
                          total_donations BIGINT DEFAULT 0,

                          image_url VARCHAR(255),

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

                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                          version BIGINT NOT NULL DEFAULT 0,

                          enabled BOOLEAN NOT NULL DEFAULT TRUE
);
-- Indexes
CREATE INDEX idx_app_user_blood_group ON app_user(blood_group);
CREATE INDEX idx_app_user_is_available ON app_user(is_available) WHERE is_available = TRUE;
CREATE INDEX idx_app_user_city ON app_user(city);
