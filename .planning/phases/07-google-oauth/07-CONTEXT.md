# Phase 7: google-oauth - Context

**Gathered:** 2026-04-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Добавить Google OAuth как first-party provider в существующий browser OAuth flow SkillHub без ломки текущих provider-ов (`github`, `gitee`) и без изменения session security model.

</domain>

<decisions>
## Implementation Decisions

### OAuth provider integration
- **OAUTH-01:** Новый provider id: `google` (Spring registration key).
- **OAUTH-02:** Google claims extraction реализуется отдельным `OAuthClaimsExtractor` по аналогии с GitHub extractor.
- **OAUTH-03:** Canonical identity ключ: `(provider='google', provider_subject)`; email используется для create/bind логики только при корректных claims.

### Security invariants
- **SEC-01:** Все redirect `returnTo` продолжают проходить через существующий sanitize/consume flow (`OAuthLoginRedirectSupport` + `OAuthLoginFlowService`), open redirect не допускается.
- **SEC-02:** Existing policy gates (`AccessPolicy`, pending/disabled handling) остаются единым enforcement point для Google login.
- **SEC-03:** Не добавлять bypass-ветки в `SecurityConfig`; интеграция только через уже существующий OAuth2 pipeline.

### UX and API behavior
- **OAUTH-04:** `/api/v1/auth/providers` и `/api/v1/auth/methods` автоматически публикуют `google` при наличии `spring.security.oauth2.client.registration.google.*`.
- **OAUTH-05:** Login page показывает Google как обычный OAuth provider entry без hardcoded route-логики на фронте.
- **OAUTH-06:** Failure behavior должен оставаться совместимым с текущими страницами (`/login`, `/access-denied`, `/pending-approval`).

### Quality requirements
- **QA-01:** Unit + integration tests покрывают extractor behavior, provider catalog exposure, and returnTo propagation for Google.
- **QA-02:** Web tests подтверждают provider rendering и redirect trigger без реального external OAuth callback.

</decisions>

<canonical_refs>
## Canonical References

- `.planning/ROADMAP.md` - Phase 7 goals and dependencies.
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/OAuthLoginFlowService.java` - orchestration owner for OAuth login.
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/oauth/OAuthClaimsExtractor.java` - provider extractor contract.
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/config/SecurityConfig.java` - OAuth2 login pipeline wiring.
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/AuthMethodCatalog.java` - provider catalog for frontend.
- `web/src/features/auth/login-button.tsx` - OAuth button renderer.
- `web/src/pages/login.tsx` - login entry and OAuth tab.
- `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/AuthControllerTest.java` - provider/method catalog behavior tests.

</canonical_refs>

<specifics>
## Specific Ideas

- Google flow должен быть внедрен расширением существующего abstraction (`OAuthClaimsExtractor`), а не условными ветками в `OAuthLoginFlowService`.
- UI provider rendering должен оставаться data-driven через `/api/v1/auth/methods`.

</specifics>

<deferred>
## Deferred Ideas

- Multi-provider account linking UI.
- Provider-specific avatar refresh rules beyond existing login-time sync.
- Additional social providers besides Google.

</deferred>

---

*Phase: 07-google-oauth*
*Context gathered: 2026-04-17*
