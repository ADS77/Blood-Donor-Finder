-- SECURITY: per-OTP random salt so a leaked code_hash column cannot be
-- brute-forced offline (6-digit OTP space is only 10^6).
-- Nullable for backward compatibility with rows created before this migration;
-- verification falls back to the legacy unsalted comparison when salt IS NULL.
ALTER TABLE otp_codes
    ADD COLUMN salt VARCHAR(64);
