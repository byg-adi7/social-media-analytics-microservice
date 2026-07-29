-- Adds real email verification: new accounts start unverified and must click
-- the emailed link before they can log in.
--
-- Each column gets its own ALTER TABLE statement - H2's PostgreSQL
-- compatibility mode (used in tests) rejects the comma-separated
-- multi-column form, even though real Postgres accepts it fine.
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN verification_token VARCHAR(255);
ALTER TABLE users ADD COLUMN verification_token_expiry TIMESTAMP;
