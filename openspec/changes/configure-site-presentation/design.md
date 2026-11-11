# Design

## Product and visual baseline

Retain the current default LandingPage structure, section order, typography, spacing, graphics and interactions. There is one homepage. Remove the LANDING/CATALOG selector and the alternative CatalogHomePage implementation. The independent /skills marketplace retains its existing behavior. The rejected A/B/C comparison prototypes are private research, not product routes or features.

## Authority and storage

Reuse PostgreSQL system_setting / SystemSettingRepository and the site.presentation key. Keep orchestration in the application layer. V122 seeds the key idempotently; do not alter previously applied migrations or add a second store. Settings contain siteName, logoUrl, faviconUrl, sections and links. Preserve optimistic version checks, audit, existing SUPER_ADMIN authorization, session/CSRF protection and the public presentation whitelist.

## Region contract

sections is a fixed map of eight regions, rendered in the existing order. Each region has enabled, texts and optional items. texts maps a supported slot to a locale map (en/zh/ko/ru). items maps a supported sub-element to a boolean; absent items are visible. A missing text override uses existing i18n defaults; locale fallback is current language, custom English, existing product text. Treat text as plain content, never HTML/CSS or executable templates. Render line breaks and unbroken long text without overflowing.

| Region | Configurable presentation | Optional elements |
| --- | --- | --- |
| hero | Badge, heading, description, action labels, search label, preview labels and highlights | Preview, highlights, two actions |
| capabilities | Section heading/description and six capability card headings/descriptions | Each existing capability card |
| quickStart | Section heading/description and three access-mode headings/descriptions | Agent, CLI and Web modes |
| popular | Eyebrow, heading, description and browse label | Whole region |
| latest | Eyebrow, heading, description and browse label | Whole region |
| enterprise | Section and existing card headings/descriptions, diagram captions | Deployment, security, RBAC, access, audit and open-source cards |
| cta | Heading, description and two action labels | Each action |
| footer | Brand description, copyright, group headings and link labels | Four existing link groups |

### Supported text slots

- hero: `badge`, `title`, `description`, `primaryActionLabel`, `secondaryActionLabel`, `searchPlaceholder`, `previewTitle`, `previewCaption`, `previewPublishTitle`, `previewPublishDescription`, `highlightSelfHosted`, `highlightGovernance`, `highlightMultiClient`, `highlightTraceable`.
- capabilities: `eyebrow`, `title`, `description`, `secureTitle`, `secureDescription`, `communityTitle`, `communityDescription`, `integrationTitle`, `integrationDescription`, `versionControlTitle`, `versionControlDescription`, `cliTitle`, `cliDescription`, `governanceTitle`, `governanceDescription`.
- quickStart: `title`, `description`, `agentTitle`, `agentDescription`, `cliTitle`, `cliDescription`, `webTitle`, `webDescription`.
- popular: `eyebrow`, `title`, `description`, `viewAllLabel`.
- latest: `eyebrow`, `title`, `description`, `viewAllLabel`.
- enterprise: `eyebrow`, `title`, `description`, `deploymentTitle`, `deploymentDescription`, `securityTitle`, `securityDescription`, `rbacTitle`, `rbacDescription`, `accessTitle`, `accessDescription`, `auditTitle`, `auditDescription`, `openSourceTitle`, `openSourceDescription`, `clientLayer`, `serviceLayer`, `storageLayer`, `registryLabel`, `networkBoundary`.
- cta: `title`, `description`, `primaryActionLabel`, `secondaryActionLabel`.
- footer: `description`, `copyright`, `productTitle`, `developersTitle`, `projectTitle`, `resourcesTitle`, `marketLabel`, `publishLabel`, `consoleLabel`, `documentationLabel`, `cliPackageLabel`, `repositoryLabel`, `licenseLabel`, `contributingLabel`, `changelogLabel`, `codeOfConductLabel`, `communityLabel`, `privacyLabel`, `termsLabel`.

Headings (title and *Title) allow 160 characters, descriptions (*Description and description) and footer copyright allow 1000, other labels allow 160. The site name allows 80. Each text map supports at most four known locales. Reject unknown regions, text slots, element keys or link slots rather than persisting arbitrary presentation configuration. Defaults are all eight regions enabled with empty text/item maps and no link overrides.

links has nine fixed positions: repository, documentation, cliDocumentation, cliPackage, license, contributing, changelog, codeOfConduct and community. Missing overrides retain the exact default URL at each consumer. Overrides accept valid HTTP/HTTPS destinations or a single-slash root-relative path resolved with the deployment base path. Reject protocol-relative URLs, credentials, backslashes, control characters including encoded forms, dangerous schemes, missing hosts and invalid ports. No URL fetch or DNS lookup is performed. Internal publish/search/dashboard/privacy/terms destinations remain code-owned.

CLI executable commands, package names, registry/API endpoints, database/storage product names, and live skill coordinates are functional contracts, not rebrandable display text. Use the site name for appropriate nonfunctional brand labels. Keep fixed demo content identified as demonstration; operators may hide the hero preview or individual access modes. Configuration must not turn a demonstration into a claim that a skill exists or that an operation succeeded. Existing business authentication/visibility remains authoritative, and disabled popular/latest regions do not issue their queries.

## Legacy persisted settings

The draft previously stored homepageMode, heroTitle, heroDescription, showPopular, showLatest, showQuickStart and footerVisible. Only when the persisted root has no sections key, map heroTitle/heroDescription into hero texts and old visibility booleans into the corresponding regions. Ignore homepageMode and always render the original LandingPage. A new sections key takes precedence even when its maps are empty; explicitly clearing overrides restores defaults instead of reviving old fields. Compatibility reads never write the database or increment version. A successful save writes only the new shape. The unreleased old PUT body is not a long-term public contract: require nonempty sections on writes to avoid accidentally resetting configuration.

## API and assets

Keep GET /api/v1/site-presentation, SUPER_ADMIN GET/PUT /api/v1/admin/system-config/presentation, POST /api/v1/admin/system-config/presentation/assets and controlled public GET /api/v1/site-presentation/assets/{assetId}. JSON envelopes remain unchanged. PUT carries settings and expected version. Assets remain PNG/JPEG <=2MiB and <=1024px per dimension, verified and normalized to PNG in existing object storage. Asset references remain controlled brand UUID routes, separate from configurable navigation links. Uploaded brand images are publicly readable before settings Save; historical uploads are retained.

## Management UI

Use the existing form components and tokens. Expose three groups only: site information (name, logo, favicon, documentation and repository links), homepage introduction (one content-language selector, hero title and description), and homepage content (eight region visibility switches). Do not render per-region text editors, item switches, extra link slots, a preview dialog or a homepage mode selector.

Retain the stored schema for compatibility, but every edit must preserve supported fields not exposed on this page. One reset action clears only the editable brand fields, hero title/description in all supported languages, eight enabled flags and the two exposed links. It must not clear other stored text, item switches or links. Reset changes the draft until Save. Retain failed/conflicting drafts and leave protection. After successful Save provide a normal homepage link; no simulated preview is needed. Personal theme and language remain independent.

## Deployment and acceptance

No new one-click flags, environment variables, image mounts, database tables or dependencies. Save takes effect on refresh/refetch without restarting containers. Back up both the existing database and object storage. Verify the original unconfigured homepage against the baseline, all region/element switches, representative text slots across all eight regions, safe links and deployment subpaths, locale/default fallback, legacy JSON compatibility, explicit clearing, permissions/CSRF/conflicts, responsive themes, persistence and unchanged marketplace behavior. Keep one local preview for human acceptance; distinguish browser fixtures from real backend writes.
