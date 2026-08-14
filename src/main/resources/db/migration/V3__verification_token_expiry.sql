-- Give the email verification token a lifetime so a leaked token is not a
-- permanently reusable credential. Tokens are retained after successful
-- verification (re-verification stays an idempotent no-op) and only their
-- expiry is what ultimately invalidates them.
ALTER TABLE users
    ADD COLUMN verification_token_expires_at DATETIME(6) NULL AFTER verification_token;
