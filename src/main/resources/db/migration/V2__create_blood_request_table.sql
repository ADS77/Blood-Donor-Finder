CREATE TABLE blood_request (
                               id BIGSERIAL PRIMARY KEY,
                               requester_id BIGINT NOT NULL,
                               blood_group VARCHAR(20) NOT NULL,
                               message VARCHAR(1000),
                               request_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                               required_quantity INTEGER,

    -- GeoLocation
                               address VARCHAR(500),
                               city VARCHAR(100) NOT NULL,
                               district VARCHAR(100),
                               latitude DECIMAL (10,6),
                               longitude DECIMAL (10,6),
                               zipcode VARCHAR(20),

    -- Foreign key to app_user
                               CONSTRAINT fk_requester
                                   FOREIGN KEY (requester_id)
                                       REFERENCES app_user(id)
                                       ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_blood_request_requester ON blood_request(requester_id);
CREATE INDEX idx_blood_request_blood_group ON blood_request(blood_group);
CREATE INDEX idx_blood_request_status ON blood_request(status);
CREATE INDEX idx_blood_request_city ON blood_request(city);
CREATE INDEX idx_blood_request_location ON blood_request(latitude, longitude)
    WHERE latitude IS NOT NULL AND longitude IS NOT NULL;