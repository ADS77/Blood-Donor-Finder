-- Users created by the OTP registration flow were marked as inactive before
-- verification, but the verification step never flipped the flag back on.
-- Activate every already-verified account so the login flow can enforce the
-- account-active invariant without locking out existing users.
UPDATE app_user
SET is_active = TRUE
WHERE is_verified = TRUE
  AND is_active = FALSE;
