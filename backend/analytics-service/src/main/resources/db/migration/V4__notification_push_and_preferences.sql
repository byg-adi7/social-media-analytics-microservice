-- Richer notification payloads: a short display title, an opaque JSON blob
-- for frontend deep-linking, and the timestamp a notification was actually
-- read (not just the boolean flag).
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS title VARCHAR(200);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS data TEXT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS read_at TIMESTAMP(6);

-- Widen the notification type CHECK constraint for the new event types
-- (WELCOME, ANALYSIS_COMPLETED, SYNC_SUCCESS, PASSWORD_CHANGED,
-- NEW_DEVICE_LOGIN, SUBSCRIPTION_SUCCESS, SUBSCRIPTION_EXPIRING - see
-- NotificationType.java).
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN (
        'WELCOME', 'ACCOUNT_CONNECTED', 'ANALYSIS_COMPLETED', 'SYNC_SUCCESS',
        'SYNC_FAILURE', 'REPORT_READY', 'PASSWORD_CHANGED', 'NEW_DEVICE_LOGIN',
        'SUBSCRIPTION_SUCCESS', 'SUBSCRIPTION_EXPIRING'
    ));

-- Registered push-notification device tokens (Firebase Cloud Messaging).
-- One row per physical device install; a re-registration of the same token
-- (app reinstall, token refresh callback firing again) upserts in place
-- rather than creating a duplicate row.
CREATE TABLE IF NOT EXISTS device_tokens (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    token VARCHAR(4096) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    last_used_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT device_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT device_tokens_platform_check CHECK (platform IN ('IOS', 'ANDROID', 'WEB')),
    CONSTRAINT uk_device_tokens_token UNIQUE (token)
);

CREATE INDEX IF NOT EXISTS idx_device_tokens_user_id ON device_tokens (user_id);

-- One row per user; created lazily on first read/write rather than at
-- signup, so a user who never touches notification settings never gets a
-- row at all.
CREATE TABLE IF NOT EXISTS notification_preferences (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT notification_preferences_pkey PRIMARY KEY (id),
    CONSTRAINT uk_notification_preferences_user_id UNIQUE (user_id)
);
