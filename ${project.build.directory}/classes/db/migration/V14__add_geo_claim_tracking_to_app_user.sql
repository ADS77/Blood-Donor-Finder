ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS geo_claimed_at TIMESTAMP;

CREATE INDEX idx_app_user_geo_status_claimed_at ON app_user (geo_status, geo_claimed_at);