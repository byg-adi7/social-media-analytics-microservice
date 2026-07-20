-- Baseline: captured from the exact schema Hibernate's ddl-auto=update
-- had been generating for this service (via a live pg_dump), not
-- hand-written from the entity mapping - guarantees this migration
-- produces identical DDL to what was already running.
CREATE TABLE users (
    id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT uk_6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email)
);
