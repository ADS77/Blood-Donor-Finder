-- Document verification workflow: system admins review uploaded medical
-- documents (blood reports / diagnostic reports). Approving a medical
-- document flags the owner as a verified donor.
ALTER TABLE document
    ADD COLUMN verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    ADD COLUMN verified_by UUID,
    ADD COLUMN verified_at TIMESTAMP,
    ADD COLUMN rejection_reason VARCHAR(500);

-- admin review queue: unverified documents, oldest first
CREATE INDEX idx_document_verification_queue
    ON document (created_at)
    WHERE verification_status = 'UNVERIFIED';

-- dispute adjudication: admin resolution note + pending-approval expiry sweep
ALTER TABLE blood_request
    ADD COLUMN admin_note VARCHAR(500);
