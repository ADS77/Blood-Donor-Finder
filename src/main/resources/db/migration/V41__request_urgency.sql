-- Request urgency + auto-expiry + radius escalation.
-- ROUTINE requests simply expire; URGENT/CRITICAL requests that stay
-- unanswered are re-broadcast to a widening radius of nearby donors
-- (RequestEscalationWorker) until donors are alerted or the TTL passes.
ALTER TABLE blood_request
    ADD COLUMN urgency VARCHAR(20) NOT NULL DEFAULT 'ROUTINE',
    ADD COLUMN expires_at TIMESTAMP,
    ADD COLUMN escalation_level INT NOT NULL DEFAULT 0,
    ADD COLUMN last_escalated_at TIMESTAMP,
    ADD COLUMN alerted_user_ids JSONB NOT NULL DEFAULT '[]'::jsonb;

-- pending urgent/critical requests that are still unanswered.
-- request_time is the creation timestamp on this table (there is no created_at).
CREATE INDEX idx_blood_request_escalation
    ON blood_request (urgency, request_time)
    WHERE status = 'PENDING' AND urgency IN ('URGENT', 'CRITICAL');

ALTER TYPE notification_type ADD VALUE 'URGENT_REQUEST_ALERT';
ALTER TYPE notification_type ADD VALUE 'DOCUMENT_VERIFIED';
