CREATE TABLE IF NOT EXISTS query_history (
    id UUID PRIMARY KEY,
    engine VARCHAR(50) NOT NULL,
    original_query TEXT NOT NULL,
    success BOOLEAN NOT NULL,
    error_count INT NOT NULL DEFAULT 0,
    warning_count INT NOT NULL DEFAULT 0,
    errors JSONB,
    warnings JSONB,
    suggestions JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_query_history_created_at
ON query_history (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_query_history_engine
ON query_history (engine);

CREATE INDEX IF NOT EXISTS idx_query_history_success
ON query_history (success);
