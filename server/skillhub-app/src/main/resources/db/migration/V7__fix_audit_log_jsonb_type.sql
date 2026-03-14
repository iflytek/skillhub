-- Fix audit_log detail_json column to properly handle JSONB type
-- This migration ensures existing data is compatible with the JSONB type

-- The column is already defined as jsonb in V1, but we need to ensure
-- any existing string data can be properly cast to jsonb
ALTER TABLE audit_log
ALTER COLUMN detail_json TYPE jsonb
USING CASE
    WHEN detail_json IS NULL THEN NULL
    WHEN detail_json = '' THEN NULL
    ELSE detail_json::jsonb
END;
