-- V3: Create user_settings table
CREATE TABLE user_settings (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    preferred_model   VARCHAR(100) DEFAULT 'gemini-2.0-flash',
    daily_query_limit INT NOT NULL DEFAULT 50,
    preferences       JSONB DEFAULT '{}',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_user_settings_user_id ON user_settings(user_id);
