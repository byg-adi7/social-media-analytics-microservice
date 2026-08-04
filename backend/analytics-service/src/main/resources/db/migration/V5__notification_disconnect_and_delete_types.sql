-- Widen the notification type CHECK constraint for two new event types
-- (ACCOUNT_DISCONNECTED, ACCOUNT_DELETED - see NotificationType.java).
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN (
        'WELCOME', 'ACCOUNT_CONNECTED', 'ANALYSIS_COMPLETED', 'SYNC_SUCCESS',
        'SYNC_FAILURE', 'REPORT_READY', 'PASSWORD_CHANGED', 'NEW_DEVICE_LOGIN',
        'ACCOUNT_DISCONNECTED', 'ACCOUNT_DELETED',
        'SUBSCRIPTION_SUCCESS', 'SUBSCRIPTION_EXPIRING'
    ));
