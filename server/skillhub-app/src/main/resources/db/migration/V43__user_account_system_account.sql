ALTER TABLE user_account
    ADD COLUMN system_account BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE user_account
SET system_account = TRUE
WHERE id = 'builtin-skill-publisher'
  AND display_name = 'Built-in Skill Publisher'
  AND email IS NULL
  AND avatar_url IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM local_credential
      WHERE local_credential.user_id = user_account.id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM identity_binding
      WHERE identity_binding.user_id = user_account.id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM api_token
      WHERE api_token.user_id = user_account.id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM user_role_binding
      WHERE user_role_binding.user_id = user_account.id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM namespace_member
      WHERE namespace_member.user_id = user_account.id
  );

UPDATE user_account
SET system_account = TRUE,
    display_name = 'Built-in Skill Publisher',
    email = NULL,
    avatar_url = NULL
WHERE id = 'builtin-skill-publisher'
  AND display_name = 'SkillHub Built-in Publisher'
  AND email = 'builtin-skill-publisher@example.invalid'
  AND avatar_url IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM local_credential
      WHERE local_credential.user_id = user_account.id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM identity_binding
      WHERE identity_binding.user_id = user_account.id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM api_token
      WHERE api_token.user_id = user_account.id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM user_role_binding
      WHERE user_role_binding.user_id = user_account.id
  )
  AND EXISTS (
      SELECT 1
      FROM namespace_member legacy_member
      JOIN namespace legacy_namespace ON legacy_namespace.id = legacy_member.namespace_id
      WHERE legacy_member.user_id = user_account.id
        AND legacy_namespace.slug = 'global'
        AND legacy_member.role = 'OWNER'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM namespace_member bad_member
      LEFT JOIN namespace bad_namespace ON bad_namespace.id = bad_member.namespace_id
      WHERE bad_member.user_id = user_account.id
        AND (
            bad_namespace.slug IS NULL
            OR bad_namespace.slug <> 'global'
            OR bad_member.role <> 'OWNER'
        )
  );
