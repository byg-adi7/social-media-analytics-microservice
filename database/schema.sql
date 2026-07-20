-- DEPRECATED: no longer mounted by docker-compose.yml's postgres init.
-- auth-service and analytics-service each manage their own tables via
-- Hibernate (spring.jpa.hibernate.ddl-auto=update) from their JPA
-- entities, which have diverged from this schema (UUID primary keys,
-- richer per-platform OAuth columns on social_accounts, etc.) - applying
-- this script against their tables would conflict with what Hibernate
-- generates. Kept for historical/reference purposes only. The
-- notifications/reports tables below also correspond to a
-- notification-service that was never implemented on any branch.
DROP TABLE IF EXISTS notifications CASCADE;
DROP TABLE IF EXISTS reports CASCADE;
DROP TABLE IF EXISTS analytics CASCADE;
DROP TABLE IF EXISTS social_accounts CASCADE;
DROP TABLE IF EXISTS subscriptions CASCADE;
DROP TABLE IF EXISTS users CASCADE;

CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    subscription_plan VARCHAR(50) DEFAULT 'FREE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE subscriptions (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    plan_type VARCHAR(50),
    price DECIMAL(10, 2),
    duration VARCHAR(50),
    start_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_date TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE social_accounts (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    platform VARCHAR(50) NOT NULL,
    account_username VARCHAR(255) NOT NULL,
    access_token TEXT,
    refresh_token TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE(user_id, platform, account_username)
);

CREATE TABLE analytics (
    id SERIAL PRIMARY KEY,
    social_account_id INTEGER NOT NULL,
    views BIGINT DEFAULT 0,
    likes BIGINT DEFAULT 0,
    comments BIGINT DEFAULT 0,
    shares BIGINT DEFAULT 0,
    followers BIGINT DEFAULT 0,
    engagement_rate DECIMAL(5, 2),
    date DATE DEFAULT CURRENT_DATE,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (social_account_id) REFERENCES social_accounts(id) ON DELETE CASCADE
);

CREATE TABLE reports (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    report_type VARCHAR(50),
    file_path VARCHAR(500),
    file_content BYTEA,
    generated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    start_period DATE,
    end_period DATE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE notifications (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50),
    is_read BOOLEAN DEFAULT FALSE,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_social_accounts_user_id ON social_accounts(user_id);
CREATE INDEX idx_analytics_social_account_id ON analytics(social_account_id);
CREATE INDEX idx_analytics_date ON analytics(date);
CREATE INDEX idx_reports_user_id ON reports(user_id);
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_is_read ON notifications(is_read);
CREATE INDEX idx_subscriptions_user_id ON subscriptions(user_id);

CREATE VIEW user_analytics_summary AS
SELECT
    u.id, u.name, u.email,
    COUNT(DISTINCT sa.id) as total_accounts,
    SUM(a.views) as total_views,
    SUM(a.likes) as total_likes,
    SUM(a.followers) as total_followers,
    MAX(a.timestamp) as last_sync
FROM users u
LEFT JOIN social_accounts sa ON u.id = sa.user_id
LEFT JOIN analytics a ON sa.id = a.social_account_id
GROUP BY u.id, u.name, u.email;

CREATE VIEW daily_analytics_trend AS
SELECT
    sa.id, sa.platform, a.date,
    SUM(a.views) as daily_views,
    SUM(a.likes) as daily_likes,
    SUM(a.followers) as daily_followers,
    AVG(a.engagement_rate) as avg_engagement
FROM social_accounts sa
JOIN analytics a ON sa.id = a.social_account_id
GROUP BY sa.id, sa.platform, a.date
ORDER BY a.date DESC;

INSERT INTO users (email, password, name, subscription_plan) VALUES
('demo@audienceinsights.com', '$2a$10$hash...', 'Demo User', 'FREE'),
('creator@example.com', '$2a$10$hash...', 'Content Creator', 'PREMIUM');

INSERT INTO social_accounts (user_id, platform, account_username, access_token) VALUES
(1, 'YouTube', 'demo_channel', 'token_123'),
(1, 'Instagram', 'demo_account', 'token_456'),
(2, 'TikTok', 'creator_tiktok', 'token_789');

INSERT INTO analytics (social_account_id, views, likes, comments, shares, followers, engagement_rate) VALUES
(1, 5000, 250, 45, 12, 500, 5.0),
(2, 3000, 180, 30, 8, 300, 6.0),
(3, 8000, 400, 120, 50, 1000, 5.0);
