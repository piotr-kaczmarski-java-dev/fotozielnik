-- Baseline migration. Committed migrations are immutable: change the schema with a NEW file
-- (V2__..., V3__...). See AGENTS.md "Hard rules".
CREATE TABLE IF NOT EXISTS app_metadata (
    key   VARCHAR(100) PRIMARY KEY,
    value VARCHAR(500) NOT NULL
);
