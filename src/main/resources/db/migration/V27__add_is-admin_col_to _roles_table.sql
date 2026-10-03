ALTER TABLE roles
    ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE roles
    ADD CONSTRAINT chk_roles_name_format CHECK (name ~ '^[A-Z][A-Z0-9_]{2,49}$') NOT VALID;
