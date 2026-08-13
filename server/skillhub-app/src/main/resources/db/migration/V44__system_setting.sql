-- V44__system_setting.sql
--
-- Operator-configurable platform settings.
--
-- Each row holds one setting group as a JSON document so a group can gain
-- fields without a schema migration. A row is written only when an operator
-- overrides a group; an absent row means "use the configured defaults", which
-- keeps configuration-file-only deployments working unchanged.

CREATE TABLE system_setting (
    setting_key VARCHAR(128) PRIMARY KEY,
    setting_value JSONB NOT NULL,
    updated_by VARCHAR(128) REFERENCES user_account(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
