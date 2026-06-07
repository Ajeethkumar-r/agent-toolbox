-- V2: Create user_tokens table for encrypted OAuth tokens
CREATE TABLE user_tokens (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider          VARCHAR(50) NOT NULL,
    access_token_enc  BYTEA,
    refresh_token_enc BYTEA,
    scopes            TEXT[],
    expires_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ,
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_user_tokens_user_provider UNIQUE (user_id, provider)
);

CREATE INDEX idx_user_tokens_user_id ON user_tokens(user_id);
