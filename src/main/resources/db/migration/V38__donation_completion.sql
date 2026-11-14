-- Donation completion flow:
--   ACCEPTED --(donor marks complete)--> PENDING_APPROVAL --(requester approves)--> FULFILLED
--                                          \--(requester disputes)--> DISPUTED
-- An immutable donation_record is written on approval; the donor's counters
-- (total_donations, rating, rating_count, last_donation_date) are updated in
-- the same transaction.

ALTER TABLE blood_request
    ADD COLUMN donated_at TIMESTAMP,                -- when the donor says the donation happened
    ADD COLUMN completion_requested_at TIMESTAMP,   -- donor hit "complete"
    ADD COLUMN completion_notes VARCHAR(500),
    ADD COLUMN completed_at TIMESTAMP,              -- requester approved
    ADD COLUMN disputed_at TIMESTAMP,
    ADD COLUMN dispute_reason VARCHAR(500);

CREATE INDEX idx_blood_request_pending_approval
    ON blood_request (completion_requested_at)
    WHERE status = 'PENDING_APPROVAL';

-- Immutable record of every completed donation (audit + history + unique per request)
CREATE TABLE donation_record (
    id               UUID PRIMARY KEY,
    blood_request_id UUID NOT NULL UNIQUE,
    donor_id         UUID NOT NULL,
    requester_id     UUID NOT NULL,
    blood_group      VARCHAR(20) NOT NULL,
    donated_at       TIMESTAMP NOT NULL,
    rating           SMALLINT CHECK (rating BETWEEN 1 AND 5),
    review           VARCHAR(500),
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_donation_request FOREIGN KEY (blood_request_id) REFERENCES blood_request (id) ON DELETE CASCADE,
    CONSTRAINT fk_donation_donor FOREIGN KEY (donor_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_donation_requester FOREIGN KEY (requester_id) REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX idx_donation_record_donor ON donation_record (donor_id, donated_at DESC);

-- running-average rating bookkeeping (rating = sum/count baked incrementally,
-- rating_count keeps the average exact even when a donation carries no rating)
ALTER TABLE app_user
    ADD COLUMN rating_count BIGINT NOT NULL DEFAULT 0;

-- three new notification types for the completion flow
ALTER TYPE notification_type ADD VALUE 'DONATION_COMPLETED';
ALTER TYPE notification_type ADD VALUE 'DONATION_APPROVED';
ALTER TYPE notification_type ADD VALUE 'DONATION_DISPUTED';
