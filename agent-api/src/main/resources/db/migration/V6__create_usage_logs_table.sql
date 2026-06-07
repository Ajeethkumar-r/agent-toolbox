-- V6: Create usage_logs table for rate limiting and token tracking
CREATE TABLE usage_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    query_date    DATE NOT NULL,
    query_count   INT NOT NULL DEFAULT 0,
    input_tokens  BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    model_used    VARCHAR(100),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_usage_logs_user_date UNIQUE (user_id, query_date)
);

CREATE INDEX idx_usage_logs_user_date ON usage_logs(user_id, query_date);
