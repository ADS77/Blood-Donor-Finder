-- V18__update_app_user_for_pii_and_security.sql

-- ============================================================
-- 1. Add new columns as nullable
-- ============================================================

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS first_name VARCHAR(100);

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS last_name VARCHAR(100);


-- ============================================================
-- 2. Migrate existing name data
--
-- Rule:
--   "John Doe"          -> John / Doe
--   "John Michael Doe"  -> John / Michael Doe
--
-- A single-name value is considered invalid for this migration
-- because we cannot safely determine the last name.
-- ============================================================

/*UPDATE app_user
SET
    first_name = split_part(trim(name), ' ', 1),
    last_name = substring(trim(name) FROM position(' ' IN trim(name)) + 1)
WHERE name IS NOT NULL
  AND length(trim(name)) > 0
  AND position(' ' IN trim(name)) > 0;

*/
-- ============================================================
-- 3. Validate migration
--
-- Do NOT silently invent values for records that cannot be
-- safely migrated.
-- ============================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM app_user
        WHERE first_name IS NULL
           OR last_name IS NULL
           OR length(trim(first_name)) = 0
           OR length(trim(last_name)) = 0
    ) THEN
        RAISE EXCEPTION
            'V18 failed: unable to safely migrate one or more existing names to first_name/last_name';
END IF;
END
$$;
