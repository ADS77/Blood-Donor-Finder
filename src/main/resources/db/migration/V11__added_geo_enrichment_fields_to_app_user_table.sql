ALTER TABLE app_user
    ADD COLUMN geo_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN geo_retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN geo_last_error VARCHAR(500);

UPDATE app_user
SET geo_status = 'COMPLETED'
WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

CREATE INDEX idx_app_user_geo_status ON app_user (geo_status);