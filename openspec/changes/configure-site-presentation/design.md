# Design

## Authority and storage

Reuse existing PostgreSQL system_setting and SystemSettingRepository with a dedicated site.presentation key. Keep presentation orchestration in the application layer. Seed defaults idempotently via a new migration; never overwrite existing settings during startup or upgrade. Use existing version and audit patterns. No new database, Redis cache or external dependencies.

Settings contain siteName, logoUrl, faviconUrl, homepageMode (LANDING/CATALOG), heroTitle and heroDescription locale maps, showPopular, showLatest, showQuickStart and footerVisible. Nullable text/asset overrides fall back to existing UI. Locale maps support the existing en/zh/ko/ru UI languages. Public read exposes settings and version only, not actors, auth settings or secret configuration.

## API

- GET /api/v1/site-presentation: public typed settings and version.
- GET and PUT /api/v1/admin/system-config/presentation: SUPER_ADMIN management; PUT includes expected version and complete typed settings.
- POST /api/v1/admin/system-config/presentation/assets: SUPER_ADMIN multipart file upload; returns controlled URL.
- GET /api/v1/site-presentation/assets/{assetId}: controlled immutable brand image resource.

All JSON uses existing response envelopes. Modify requests retain normal session/CSRF protection. Public permit rules are limited to reads. Asset references only address controlled branding UUIDs; no arbitrary URLs, file paths or public exposure of skill storage. Uploads accept PNG/JPEG <=2MiB and <=1024 pixels per dimension; reuse image processor to verify bytes and re-encode PNG. Favicon uses PNG. Binary assets remain in existing configured storage; DB stores references, not expiring signatures or binary blobs.

## UI

Use existing product components, typography and tokens. Add site presentation panel under current system configuration. Group brand, homepage and footer. Preview is local unsaved draft and must not affect public settings. Reset modifies draft until Save. Protect dirty edits from background refetch and navigation. Error/conflict preserves draft and explains recovery. User-selected language/theme are untouched.

A central TanStack Query resource feeds navbar, BrandMark, homepage, footer, title and favicon. Perform bounded initial loading before page display to avoid custom-to-default brand flash; configuration-read failure must preserve a usable default interface and expose a retry path. No request loop. The configured homepage is the actual `/` route (LandingPage), not the separately routed `/skills` market (HomePage). LANDING preserves the existing LandingPage composition by default. CATALOG uses a compact catalog composition at `/`. The `/skills` route retains its existing market sections independently of homepage switches. Disable hidden homepage section queries, and avoid mounting the unused layout or issuing duplicate queries. Resolve image URLs against actual runtime API/base path. Failed images use built-in default.

## Deployment and compatibility

No new environment settings, image mounts or one-click flags. Manage through UI after login. Saving does not require container restart; current client invalidates shared configuration and other clients pick changes up on refresh/refetch. Existing DB and configured object storage must both be persisted and backed up. New fields use defaults; ordinary upgrades do not reset administrator choices. Reverting an application image is not a database restore.

## Alternatives

Frontend-only env/config files would require mounting and restart semantics across deployment types and duplicate the existing DB settings facility. A generic settings builder or full CMS adds unnecessary dependencies and scope. Database-backed typed settings reuse the current platform and permit clear edit authorization and concurrency control. Rich white-label themes and arbitrary custom navigation remain separate work.
