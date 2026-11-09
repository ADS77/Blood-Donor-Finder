CREATE TABLE processed_events (
                                  event_id     VARCHAR(255) PRIMARY KEY,
                                  topic_name   VARCHAR(100) NOT NULL,
                                  aggregate_id VARCHAR(255) NOT NULL,
                                  processed_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_processed_events_aggregate ON processed_events (aggregate_id);