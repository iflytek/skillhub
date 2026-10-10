-- Backfill legacy public OAuth identities into platform-scoped V2 connections. The legacy table
-- remains authoritative in LEGACY/SHADOW modes and is intentionally retained for rollback.

INSERT INTO login_connection (
    id,
    public_handle,
    scope_type,
    organization_id,
    system_key,
    display_name,
    status,
    adapter_key,
    created_at,
    updated_at
)
SELECT
    'legacy-oauth-' || md5('skillhub:legacy-oauth:' || provider_code),
    'legacy-' || replace(gen_random_uuid()::text, '-', ''),
    'PLATFORM',
    NULL,
    'legacy.oauth.' || provider_code,
    'Legacy OAuth (' || provider_code || ')',
    'ACTIVE',
    'legacy-oauth',
    MIN(created_at),
    MAX(updated_at)
FROM identity_binding
GROUP BY provider_code;

INSERT INTO external_identity (
    id,
    organization_id,
    connection_id,
    issuer,
    subject_type,
    subject_value,
    user_id,
    status,
    last_authenticated_at,
    created_at,
    updated_at
)
SELECT
    'legacy-binding-' || binding.id,
    NULL,
    'legacy-oauth-' || md5('skillhub:legacy-oauth:' || binding.provider_code),
    'urn:skillhub:legacy-oauth',
    'legacy-oauth-subject',
    binding.subject,
    binding.user_id,
    'ACTIVE',
    NULL,
    binding.created_at,
    binding.updated_at
FROM identity_binding binding;

-- Fail the migration transaction if either the cardinality or canonical binding digest differs.
-- MD5 is a PostgreSQL built-in used here as a migration consistency checksum, not as a credential
-- or cryptographic trust primitive.
DO $$
DECLARE
    legacy_count BIGINT;
    v2_count BIGINT;
    legacy_digest TEXT;
    v2_digest TEXT;
BEGIN
    SELECT
        COUNT(*),
        md5(COALESCE(string_agg(
            jsonb_build_array(provider_code, subject, user_id)::text,
            E'\n' ORDER BY provider_code, subject, user_id
        ), ''))
    INTO legacy_count, legacy_digest
    FROM identity_binding;

    SELECT
        COUNT(*),
        md5(COALESCE(string_agg(
            jsonb_build_array(
                substring(connection.system_key FROM length('legacy.oauth.') + 1),
                identity.subject_value,
                identity.user_id
            )::text,
            E'\n' ORDER BY connection.system_key, identity.subject_value, identity.user_id
        ), ''))
    INTO v2_count, v2_digest
    FROM external_identity identity
    JOIN login_connection connection ON connection.id = identity.connection_id
    WHERE connection.scope_type = 'PLATFORM'
      AND connection.adapter_key = 'legacy-oauth'
      AND connection.system_key LIKE 'legacy.oauth.%'
      AND identity.organization_id IS NULL
      AND identity.issuer = 'urn:skillhub:legacy-oauth'
      AND identity.subject_type = 'legacy-oauth-subject';

    IF legacy_count <> v2_count OR legacy_digest <> v2_digest THEN
        RAISE EXCEPTION
            'legacy identity V2 backfill verification failed: legacy_count=%, v2_count=%, legacy_digest=%, v2_digest=%',
            legacy_count, v2_count, legacy_digest, v2_digest;
    END IF;
END $$;
