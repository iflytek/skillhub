-- Keep installation-specific presentation settings across image upgrades and restarts.
-- Empty settings use product defaults; existing values must never be overwritten.
INSERT INTO system_setting(setting_key, value_json)
VALUES ('site.presentation', '{}'::jsonb)
ON CONFLICT (setting_key) DO NOTHING;
