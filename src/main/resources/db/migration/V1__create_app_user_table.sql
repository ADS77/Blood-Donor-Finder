CREATE TABLE app_user (
                          id BIGSERIAL PRIMARY KEY,
                          name VARCHAR(255),
                          email VARCHAR(255) UNIQUE NOT NULL,
                          phone VARCHAR(255) UNIQUE NOT NULL,
                          blood_group VARCHAR(20),
                          role VARCHAR(50),
                          verified BOOLEAN DEFAULT FALSE,
                          is_available BOOLEAN DEFAULT FALSE,
                          last_donation_date TIMESTAMP,
                          rating DOUBLE PRECISION DEFAULT 0.0,
                          total_donations BIGINT DEFAULT 0,
                          image_url VARCHAR(500),
                          -- Geolocation
                          address VARCHAR(500),
                          city VARCHAR(100) NOT NULL,
                          district VARCHAR(100),
                          latitude DECIMAL (10,6),
                          longitude DECIMAL (10,6),
                          zipcode VARCHAR(20),

                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_app_user_email ON app_user(email);
CREATE INDEX idx_app_user_phone ON app_user(phone);
CREATE INDEX idx_app_user_blood_group ON app_user(blood_group);
CREATE INDEX idx_app_user_is_available ON app_user(is_available) WHERE is_available = TRUE;
CREATE INDEX idx_app_user_city ON app_user(city);
