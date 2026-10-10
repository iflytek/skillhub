# Enterprise Identity R1-A Local Review Checklist

This checklist is for the current local reference branch `feature/enterprise-login-release`.
It does not authorize commit, push, PR creation, deployment, or production configuration changes.

## Review package A — OpenSpec and rollout boundary

Read first:

- `proposal.md`
- `design.md`
- `rollout-plan.md`
- `tasks.md`
- `verification.md`
- `specs/federated-authentication/spec.md`
- `specs/enterprise-identity-governance/spec.md`
- `specs/enterprise-organizations/spec.md`

Check:

- R1-A means public OAuth enters the unified identity-core decision gate.
- Legacy `identity_binding` remains the runtime write and rollback authority for public OAuth.
- V60 shadow backfill is described as consistency/rollback support, not as V2 write-authority cutover.
- Feishu/DingTalk real adapters, SCIM/Directory/Group sync, Namespace entitlement, SAML, CAS and LDAP are non-goals for the first merge batch.
- Rollback order is runtime switches first; no destructive migration rollback.

## Review package B — backend authentication compatibility

Main files:

- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/LegacyPlatformIdentityCore.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/LegacyPlatformIdentityDecision.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/LegacyPlatformIdentityCoreBridge.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/OAuthLoginFlowService.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/OAuthLoginRedirectSupport.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/OAuth2LoginSuccessHandler.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/AuthMethodCatalog.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/EnterpriseLoginAppService.java`

Check:

- LEGACY and SHADOW do not change existing login outcome.
- ACTIVE fails closed before active or pending legacy binding writes when unified core returns Denied/Conflict or invalid resolution.
- OAuth success ignores saved API requests and defaults to `/` unless a safe app-relative return target exists.
- Auth catalog shows public OAuth only when a real non-placeholder client ID exists.
- Auth catalog shows enterprise discovery only when control switch, login switch, allowlist and ACTIVE identity core all permit it.
- Enterprise discovery fallback public methods do not include `ENTERPRISE_DISCOVERY` itself.

Targeted verification:

```bash
cd server
MAVEN_OPTS='-Xmx768m -XX:ActiveProcessorCount=2' ./mvnw -pl skillhub-app -am \
  -Dtest=AuthMethodCatalogTest,EnterpriseLoginAppServiceTest,OAuth2LoginHandlersTest,OAuthLoginRedirectSupportTest,OAuthLoginFlowServiceTest,LegacyPlatformIdentityCoreBridgeTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
MAVEN_OPTS='-Xmx768m -XX:ActiveProcessorCount=2' ./mvnw -pl skillhub-app -am -DskipTests package
```

## Review package C — configuration-driven login and registration UI

Main files:

- `web/src/pages/login.tsx`
- `web/src/pages/register.tsx`
- `web/src/features/auth/auth-shell.tsx`
- `web/src/features/auth/auth-entry-switch.tsx`
- `web/src/features/auth/enterprise-login-discovery.tsx`
- `web/src/features/auth/login-button.tsx`
- `web/src/shared/lib/auth-route.ts`
- `web/src/api/types.ts`
- `web/src/app/layout.tsx`
- `web/src/assets/login-network.webp`
- `web/src/assets/login-network.webp.json`
- `web/src/i18n/locales/{zh,en,ru}.json`

Check:

- Login and register own a dedicated full-screen shell; the marketplace header/footer do not wrap them.
- The service catalog decides visible entries; frontend has no fixed GitHub/GitLab/Feishu/DingTalk fallback.
- Enterprise account and personal account are mutually exclusive panels when both are configured.
- Public OAuth stays visible when enterprise discovery is disabled.
- No configured provider means no fake button.
- Login/register use the same safe return target rules: only app-relative paths survive; unsafe targets go `/`.
- Enterprise discovery trims the identifier before submitting to the backend but preserves typed input when the user returns to edit.
- i18n keys used by login/register/enterprise/Organization pages exist in zh/en/ru.

Targeted verification:

```bash
cd web
pnpm exec vitest run \
  src/pages/login.test.tsx \
  src/pages/register.test.tsx \
  src/features/auth/enterprise-login-discovery.test.tsx \
  src/features/auth/login-button.test.tsx \
  src/shared/lib/auth-route.test.ts \
  --maxWorkers=1
pnpm run typecheck
pnpm run lint
pnpm run build
```

## Review package D — Organization list and administration UX boundary

Main files:

- `web/src/pages/dashboard/organizations.tsx`
- `web/src/features/organization/organization-admin-shell.tsx`
- `web/src/pages/dashboard/organization-admin-pages.test.tsx`
- `web/src/features/organization/organization-admin-shell.test.ts`

Check:

- Member-only Organizations are listed but do not link to administration detail.
- Organization administration entry requires at least one Organization role:
  `ORG_OWNER`, `IDENTITY_ADMIN`, `LOGIN_SECRET_ADMIN`, `MEMBER_ADMIN`, or `ORG_AUDITOR`.
- Backend 403 remains the final authorization boundary; frontend only improves navigation.
- One account may belong to multiple Organizations; do not simplify UI or model to a single enterprise assumption.

Targeted verification:

```bash
cd web
pnpm exec vitest run \
  src/features/organization/organization-admin-shell.test.ts \
  src/pages/dashboard/organization-admin-pages.test.tsx \
  --maxWorkers=1
```

## Cross-cutting checks

Run before committing or asking for PR authorization:

```bash
openspec validate enterprise-identity-platform --strict
git diff --check
rg -n 'BEGIN (RSA|OPENSSH|PRIVATE)' .
```

If a real test token was used during manual validation, search for that exact value locally without
writing it into repository files, shell scripts, logs or PR text.

Known non-blocking warnings:

- Vite warns about runtime `runtime-config.js`, mixed static/dynamic dashboard import and large chunks.
- Java test compilation reports existing deprecated/unchecked warnings in unrelated tests.
