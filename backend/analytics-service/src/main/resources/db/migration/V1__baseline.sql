-- Baseline: captured from the exact schema Hibernate's ddl-auto=update
-- had been generating for this service (via a live pg_dump), not
-- hand-written from the entity mapping - guarantees this migration
-- produces identical DDL to what was already running.
CREATE TABLE social_accounts (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6),
    access_token VARCHAR(2000),
    account_id VARCHAR(255) NOT NULL,
    account_name VARCHAR(255),
    active BOOLEAN NOT NULL,
    connected_at TIMESTAMP(6) NOT NULL,
    last_synced TIMESTAMP(6),
    platform VARCHAR(20) NOT NULL,
    profile_image VARCHAR(1000),
    refresh_token VARCHAR(2000),
    token_expires_at TIMESTAMP(6),
    user_id UUID NOT NULL,
    username VARCHAR(255),
    CONSTRAINT social_accounts_pkey PRIMARY KEY (id),
    CONSTRAINT uk_platform_account_id UNIQUE (platform, account_id),
    CONSTRAINT social_accounts_platform_check CHECK (platform IN ('YOUTUBE', 'INSTAGRAM', 'TIKTOK', 'FACEBOOK', 'SPOTIFY'))
);

CREATE INDEX idx_social_account_platform ON social_accounts (platform);
CREATE INDEX idx_social_account_user_id ON social_accounts (user_id);

CREATE TABLE analytics (
    id UUID NOT NULL,
    analytics_date DATE NOT NULL,
    comments BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    engagement_rate DOUBLE PRECISION NOT NULL,
    followers BIGINT NOT NULL,
    following BIGINT NOT NULL,
    impressions BIGINT NOT NULL,
    likes BIGINT NOT NULL,
    posts BIGINT NOT NULL,
    profile_visits BIGINT NOT NULL,
    reach BIGINT NOT NULL,
    saves BIGINT NOT NULL,
    shares BIGINT NOT NULL,
    views BIGINT NOT NULL,
    watch_time DOUBLE PRECISION NOT NULL,
    social_account_id UUID NOT NULL,
    CONSTRAINT analytics_pkey PRIMARY KEY (id),
    CONSTRAINT uk_account_analytics_date UNIQUE (social_account_id, analytics_date),
    CONSTRAINT fkbk19w222g90m5iwp8b0cjw5xh FOREIGN KEY (social_account_id) REFERENCES social_accounts (id)
);

CREATE INDEX idx_analytics_account_id ON analytics (social_account_id);
CREATE INDEX idx_analytics_date ON analytics (analytics_date);
