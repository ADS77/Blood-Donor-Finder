-- Request → Accept → Chat pipeline.
-- A requester's broadcast blood request can be accepted by ONE verified donor;
-- acceptance atomically assigns the donor and opens exactly one chat per request.

ALTER TABLE blood_request
    ADD COLUMN accepted_donor_id UUID,
    ADD COLUMN accepted_at TIMESTAMPTZ;

ALTER TABLE blood_request
    ADD CONSTRAINT fk_blood_request_accepted_donor
    FOREIGN KEY (accepted_donor_id) REFERENCES app_user (id);

CREATE INDEX idx_blood_request_accepted_donor ON blood_request (accepted_donor_id);

CREATE TYPE chat_status AS ENUM ('ACTIVE', 'CLOSED');

-- Exactly one chat per accepted request.
CREATE TABLE chat (
    id           UUID PRIMARY KEY,
    request_id   UUID NOT NULL UNIQUE,
    requester_id UUID NOT NULL,
    donor_id     UUID NOT NULL,
    status       chat_status NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_chat_request   FOREIGN KEY (request_id)   REFERENCES blood_request (id),
    CONSTRAINT fk_chat_requester FOREIGN KEY (requester_id) REFERENCES app_user (id),
    CONSTRAINT fk_chat_donor     FOREIGN KEY (donor_id)     REFERENCES app_user (id)
);

CREATE INDEX idx_chat_requester ON chat (requester_id, created_at DESC);
CREATE INDEX idx_chat_donor     ON chat (donor_id, created_at DESC);

CREATE TABLE chat_message (
    id         UUID PRIMARY KEY,
    chat_id    UUID NOT NULL,
    sender_id  UUID NOT NULL,
    body       VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    read_at    TIMESTAMP,
    CONSTRAINT fk_chat_message_chat   FOREIGN KEY (chat_id)   REFERENCES chat (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_message_sender FOREIGN KEY (sender_id) REFERENCES app_user (id)
);

-- keyset pagination + unread counts
CREATE INDEX idx_chat_message_chat_created ON chat_message (chat_id, created_at);
CREATE INDEX idx_chat_message_unread ON chat_message (chat_id, read_at) WHERE read_at IS NULL;
