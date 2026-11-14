-- Document service: user-uploaded files (profile photo, blood & diagnostic reports).
-- Postgres holds the metadata / source of truth; bytes live in MinIO (S3 API)
-- under fine-grained, server-generated object keys — never client-controlled.
CREATE TABLE document (
    id                UUID PRIMARY KEY,
    owner_id          UUID NOT NULL,
    doc_type          VARCHAR(40) NOT NULL,   -- PROFILE_PHOTO | BLOOD_REPORT | DIAGNOSTIC_REPORT
    bucket            VARCHAR(63) NOT NULL,   -- sondhan-profile | sondhan-medical
    object_key        VARCHAR(512) NOT NULL,  -- users/{ownerId}/{folder}/{yyyy}/{mm}/{uuid}.{ext}
    original_filename VARCHAR(255),           -- display only, never used in keys
    content_type      VARCHAR(100) NOT NULL,  -- verified against magic bytes at upload
    size_bytes        BIGINT NOT NULL CHECK (size_bytes > 0),
    checksum_sha256   CHAR(64),               -- integrity + future dedupe
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | DELETED
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMP,
    CONSTRAINT fk_document_owner FOREIGN KEY (owner_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT uq_document_object_key UNIQUE (object_key)
);

-- per-user document list, newest first (keyset pagination)
CREATE INDEX idx_document_owner_created ON document (owner_id, created_at DESC);
-- "my blood reports" style filtered listing
CREATE INDEX idx_document_owner_type ON document (owner_id, doc_type, created_at DESC);
