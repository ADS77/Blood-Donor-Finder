-- Hibernate maps String to VARCHAR; the original CHAR(64) column fails schema
-- validation. CHAR also pads with spaces, which would corrupt stored checksums.
ALTER TABLE document
    ALTER COLUMN checksum_sha256 TYPE VARCHAR(64);
