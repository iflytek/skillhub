-- Old binaries can persist arbitrary scope names. Only the new issuer sets this marker.
ALTER TABLE api_token ADD COLUMN namespace_scopes_issued BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE api_token t
SET scope_json = (
    SELECT COALESCE(jsonb_agg(value ORDER BY ord), '[]'::jsonb)
    FROM jsonb_array_elements(t.scope_json) WITH ORDINALITY AS item(value, ord)
    WHERE btrim(value #>> '{}', E' \t\r\n') NOT IN ('namespace:read', 'namespace:manage')
       OR value = 'null'::jsonb
)
WHERE jsonb_typeof(scope_json) = 'array' AND EXISTS (
    SELECT 1 FROM jsonb_array_elements(CASE WHEN jsonb_typeof(scope_json) = 'array' THEN scope_json ELSE '[]'::jsonb END) item
    WHERE btrim(item #>> '{}', E' \t\r\n') IN ('namespace:read', 'namespace:manage')
);
