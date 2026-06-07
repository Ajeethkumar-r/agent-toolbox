-- V9: Fix audit_logs RLS policy to use missing-ok parameter (consistent with other tables)
DROP POLICY IF EXISTS user_isolation ON audit_logs;
CREATE POLICY user_isolation ON audit_logs
    USING (user_id = current_setting('app.current_user_id', true)::uuid);
