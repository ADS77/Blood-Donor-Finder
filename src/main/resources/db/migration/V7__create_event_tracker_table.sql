CREATE TABLE event_tracker (
                               id BIGSERIAL PRIMARY KEY,
                               event_id VARCHAR(255) NOT NULL,
                               event_type VARCHAR(255) NOT NULL,
                               status VARCHAR(255) NOT NULL,
                               processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               error_message TEXT,
                               CONSTRAINT uk_event_tracker_event_id UNIQUE (event_id)
);

CREATE INDEX idx_event_id ON event_tracker(event_id);
