-- Baseline: captured from the exact schema Hibernate's ddl-auto=update
-- had been generating for this service (via a live pg_dump), not
-- hand-written from the entity mapping - guarantees this migration
-- produces identical DDL to what was already running.
CREATE TABLE notifications (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    is_read BOOLEAN NOT NULL,
    type VARCHAR(30) NOT NULL,
    user_id UUID NOT NULL,
    CONSTRAINT notifications_pkey PRIMARY KEY (id),
    CONSTRAINT notifications_type_check CHECK (type IN ('ACCOUNT_CONNECTED', 'SYNC_FAILURE', 'REPORT_READY'))
);

CREATE INDEX idx_notification_user_id ON notifications (user_id);

CREATE TABLE reports (
    id UUID NOT NULL,
    content TEXT,
    end_period DATE NOT NULL,
    error_message VARCHAR(1000),
    generated_at TIMESTAMP(6) NOT NULL,
    report_type VARCHAR(30) NOT NULL,
    start_period DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    user_id UUID NOT NULL,
    CONSTRAINT reports_pkey PRIMARY KEY (id),
    CONSTRAINT reports_report_type_check CHECK (report_type IN ('PLATFORM_COMPARISON', 'SUMMARY')),
    CONSTRAINT reports_status_check CHECK (status IN ('COMPLETED', 'FAILED'))
);

CREATE INDEX idx_report_user_id ON reports (user_id);
