CREATE TABLE audit_logs_default
    PARTITION OF audit_logs
    DEFAULT;