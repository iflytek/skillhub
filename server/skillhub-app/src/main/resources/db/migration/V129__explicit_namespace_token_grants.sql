-- Old binaries can persist arbitrary scope names. Only the new issuer sets this marker.
ALTER TABLE api_token ADD COLUMN namespace_scopes_issued BOOLEAN NOT NULL DEFAULT FALSE;
