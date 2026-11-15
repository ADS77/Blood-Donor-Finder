-- GeoLocation is a shared @Embeddable used by both app_user and blood_request.
-- The freshness columns were only added to app_user (V34), so Hibernate's
-- schema validation fails for blood_request. They are not used by requests,
-- but the shared embeddable requires the columns to exist on every table.
ALTER TABLE blood_request
    ADD COLUMN IF NOT EXISTS geo_updated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS geo_reconfirm_requested_at TIMESTAMPTZ;
