-- Transactional outbox for email notifications.
--
-- Business transactions insert a row here instead of calling the SMTP
-- transport directly. A scheduled relay publishes PENDING rows to Kafka and
-- only marks them PUBLISHED after the broker acknowledges the send. The email
-- consumer then marks the row DELIVERED (or FAILED after retries are
-- exhausted). This keeps the database and Kafka consistent without a
-- distributed transaction.
CREATE TABLE email_outbox (
    id              UUID PRIMARY KEY,
    event_id        VARCHAR(255) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    topic_name      VARCHAR(150) NOT NULL,
    payload         TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL,
    attempts        INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error      TEXT,
    locked_at       TIMESTAMPTZ,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_email_outbox_event_id UNIQUE (event_id),
    CONSTRAINT ck_email_outbox_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'DELIVERED', 'FAILED')
    )
);

-- The relay claims the oldest due rows. The partial index keeps the hot
-- dispatch query small even when the table contains a long delivery history.
CREATE INDEX idx_email_outbox_dispatch
    ON email_outbox (next_attempt_at, created_at)
    WHERE status = 'PENDING';

-- Used to recover rows abandoned by a relay instance that crashed between
-- claiming a row and updating its final state.
CREATE INDEX idx_email_outbox_processing_lock
    ON email_outbox (locked_at)
    WHERE status = 'PROCESSING';

-- Used by the retention job for terminal rows.
CREATE INDEX idx_email_outbox_status_updated
    ON email_outbox (status, updated_at);
