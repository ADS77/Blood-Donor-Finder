-- Align the new tables with the JPA mappings so Hibernate schema validation
-- (ddl-auto=validate) passes.
--
-- 1. Instant fields map to TIMESTAMP WITH TIME ZONE. The request/donation
--    timestamps were created as plain TIMESTAMP; convert them, interpreting the
--    existing values as UTC (the application already writes UTC).
-- 2. Native enum columns (chat.status, notification.type) are mapped as
--    @Enumerated(STRING) -> VARCHAR by the entities, so store them as VARCHAR.
-- 3. donation_record.rating is an Integer in the entity; SMALLINT is too narrow.

ALTER TABLE blood_request
    ALTER COLUMN geo_claimed_at TYPE TIMESTAMPTZ USING geo_claimed_at AT TIME ZONE 'UTC',
    ALTER COLUMN donated_at TYPE TIMESTAMPTZ USING donated_at AT TIME ZONE 'UTC',
    ALTER COLUMN completion_requested_at TYPE TIMESTAMPTZ USING completion_requested_at AT TIME ZONE 'UTC',
    ALTER COLUMN completed_at TYPE TIMESTAMPTZ USING completed_at AT TIME ZONE 'UTC',
    ALTER COLUMN disputed_at TYPE TIMESTAMPTZ USING disputed_at AT TIME ZONE 'UTC';

ALTER TABLE donation_record
    ALTER COLUMN donated_at TYPE TIMESTAMPTZ USING donated_at AT TIME ZONE 'UTC',
    ALTER COLUMN rating TYPE INTEGER;

-- Drop the enum-typed default first; it depends on the enum type.
ALTER TABLE chat ALTER COLUMN status DROP DEFAULT;
ALTER TABLE chat
    ALTER COLUMN status TYPE VARCHAR(20) USING status::text;
ALTER TABLE chat ALTER COLUMN status SET DEFAULT 'ACTIVE';

ALTER TABLE notification
    ALTER COLUMN type TYPE VARCHAR(40) USING type::text;

-- The native enum types are no longer referenced by any column.
DROP TYPE IF EXISTS chat_status;
DROP TYPE IF EXISTS notification_type;
