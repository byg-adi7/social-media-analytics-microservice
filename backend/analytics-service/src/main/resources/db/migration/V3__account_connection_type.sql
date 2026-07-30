-- Distinguishes a live OAuth-connected account from one created by
-- uploading a CSV of daily metrics (a user may have both, for the same
-- platform, at once - see AccountConnectionType).
ALTER TABLE social_accounts
    ADD COLUMN connection_type VARCHAR(20) NOT NULL DEFAULT 'OAUTH';

ALTER TABLE social_accounts
    ADD CONSTRAINT social_accounts_connection_type_check
    CHECK (connection_type IN ('OAUTH', 'CSV_IMPORT'));

-- Adds TWITTER: no real API integration exists for it, so CSV import is the
-- only way to get its data into the platform (see Platform.java).
ALTER TABLE social_accounts DROP CONSTRAINT social_accounts_platform_check;
ALTER TABLE social_accounts
    ADD CONSTRAINT social_accounts_platform_check
    CHECK (platform IN ('YOUTUBE', 'INSTAGRAM', 'TIKTOK', 'FACEBOOK', 'SPOTIFY', 'TWITTER'));
