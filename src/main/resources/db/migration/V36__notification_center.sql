-- Facebook-style notification center.
-- Every product event (chat message, request acceptance, location reconfirmation)
-- creates a notification row + realtime WS push. Email is only sent as a DAILY
-- DIGEST by NotificationDigestWorker — never per event.
CREATE TYPE notification_type AS ENUM ('CHAT_MESSAGE', 'REQUEST_ACCEPTED', 'LOCATION_RECONFIRMATION');

CREATE TABLE notification (
    id              UUID PRIMARY KEY,
    recipient_id    UUID NOT NULL,
    type            notification_type NOT NULL,
    payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    -- entity the notification deep-links to (chat id, request id, ...)
    reference_id    UUID,
    read_at         TIMESTAMP,
    -- set when the row was included in a digest email (digest cooldown tracking)
    email_digest_at TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES app_user (id) ON DELETE CASCADE
);

-- notification list (newest first, keyset pagination)
CREATE INDEX idx_notification_recipient_created ON notification (recipient_id, created_at DESC);
-- bell badge unread count
CREATE INDEX idx_notification_unread ON notification (recipient_id, read_at) WHERE read_at IS NULL;
-- digest candidate scan
CREATE INDEX idx_notification_undigested ON notification (email_digest_at) WHERE email_digest_at IS NULL AND read_at IS NULL;
