-- Location freshness pipeline.
-- geo_updated_at: when the donor's coordinates were last CONFIRMED (device update,
--   reconfirmation or geo enrichment). Nearby searches exclude donors whose location
--   is older than the configured freshness window.
-- geo_reconfirm_requested_at: when the staleness worker last emailed this donor a
--   reconfirmation request — enforces a cooldown so donors are not spammed daily.
ALTER TABLE app_user ADD COLUMN geo_updated_at TIMESTAMPTZ;
ALTER TABLE app_user ADD COLUMN geo_reconfirm_requested_at TIMESTAMPTZ;

-- Backfill: every existing donor's location was last "confirmed" when their row was
-- last updated. Without this, all pre-existing donors would look stale on day one.
UPDATE app_user SET geo_updated_at = COALESCE(updated_at, created_at);

-- The staleness worker scans by this column every night.
CREATE INDEX idx_app_user_geo_updated_at ON app_user (geo_updated_at);
