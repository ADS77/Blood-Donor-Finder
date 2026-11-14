-- Push notifications (FCM): one row per registered device.
-- Notification center policy: every event still creates one Notification row
-- + WS push; a registered device additionally receives an FCM push.
CREATE TABLE push_token (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL,
    token        VARCHAR(4096) NOT NULL UNIQUE,
    platform     VARCHAR(10) NOT NULL,          -- ANDROID | IOS | WEB
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_push_token_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX idx_push_token_user ON push_token (user_id);
