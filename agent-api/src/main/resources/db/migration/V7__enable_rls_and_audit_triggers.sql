-- V7: Enable RLS on all user-facing tables and add updated_at triggers

-- ══════════════════════════════════════════════════════════════════
-- Auto-update trigger for updated_at
-- ══════════════════════════════════════════════════════════════════
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to all tables
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_user_tokens_updated_at BEFORE UPDATE ON user_tokens
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_user_settings_updated_at BEFORE UPDATE ON user_settings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_conversations_updated_at BEFORE UPDATE ON conversations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_messages_updated_at BEFORE UPDATE ON messages
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_usage_logs_updated_at BEFORE UPDATE ON usage_logs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- ══════════════════════════════════════════════════════════════════
-- Row Level Security
-- ══════════════════════════════════════════════════════════════════
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE usage_logs ENABLE ROW LEVEL SECURITY;

-- RLS Policies: users can only access their own data
-- The application sets app.current_user_id via SET on each connection
CREATE POLICY user_isolation_users ON users
    USING (id = current_setting('app.current_user_id', true)::uuid);

CREATE POLICY user_isolation_user_tokens ON user_tokens
    USING (user_id = current_setting('app.current_user_id', true)::uuid);

CREATE POLICY user_isolation_user_settings ON user_settings
    USING (user_id = current_setting('app.current_user_id', true)::uuid);

CREATE POLICY user_isolation_conversations ON conversations
    USING (user_id = current_setting('app.current_user_id', true)::uuid);

CREATE POLICY user_isolation_messages ON messages
    USING (conversation_id IN (
        SELECT id FROM conversations
        WHERE user_id = current_setting('app.current_user_id', true)::uuid
    ));

CREATE POLICY user_isolation_usage_logs ON usage_logs
    USING (user_id = current_setting('app.current_user_id', true)::uuid);
