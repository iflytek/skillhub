## ADDED Requirements

### Requirement: Preserve passive business semantics
Delivered by: PR-1 (passive markers and examples); PR-2 (product integration regression).

The system SHALL preserve existing static action categories and business interactions without introducing analytics dependencies into business components.

#### Scenario: Existing business operation
Delivered by: PR-1 (marker/helper assertions); PR-2 (full business regression).

- **WHEN** a user logs in, publishes, uploads, downloads or navigates with analytics disabled or unavailable
- **THEN** existing permissions, requests, keyboard behavior and business outcomes remain unchanged

### Requirement: Deploy using configuration only
Delivered by: PR-2.

The system SHALL deliver optional analytics modules with the Web artifact and accept deployment configuration without source edits or hand-written Nginx rules.

#### Scenario: Connect an existing supported platform
Delivered by: PR-2.

- **WHEN** the operator supplies valid analytics JSON, a supported adapter configuration and internal upstream origin
- **THEN** the Web startup generates safe runtime configuration and the required isolated proxy routes without an additional Nginx service

### Requirement: Default closed and fail isolated
Delivered by: PR-2 (product loader); PR-1 examples SHALL remain opt-in and inert by default.

The system SHALL default to analytics disabled and consent denied, and SHALL isolate invalid configuration and runtime analytics failures from Web and business availability.

#### Scenario: Disabled or denied collection
Delivered by: PR-2.

- **WHEN** configuration is omitted, enabled is false, or consent is denied
- **THEN** the browser does not load analytics modules or SDKs and does not send analytics requests

#### Scenario: Disabled example
Delivered by: PR-1.

- **WHEN** the deployment-owned entry remains enabled:false or consent is not approved, or no entry is injected
- **THEN** no observer is installed, no tracker is loaded and no intent is sent; importing the helper alone has no collection side effects

#### Scenario: Invalid configuration
Delivered by: PR-2.

- **WHEN** analytics JSON, adapter or upstream is invalid
- **THEN** only analytics is disabled with a diagnostic that does not echo raw values, while existing Web configuration remains usable

#### Scenario: Statistics service unavailable
Delivered by: PR-2 (including request-time DNS); PR-1 covers an unavailable example tracker without making a product DNS-startup guarantee.

- **WHEN** the upstream is unreachable or its hostname cannot resolve
- **THEN** the Web still starts and business remains usable without waiting for the statistics service

### Requirement: Vendor-neutral module contract
Delivered by: PR-1 (helper and example adapter); PR-2 (registered product factories and configuration).

The system SHALL separate generic configuration, rules and lifecycle from adapter-specific parameters and SDK calls. PR-2 SHALL migrate the helper to one product source under web/src/features/analytics and retain every PR-1 test assertion rather than maintaining duplicate implementations.

#### Scenario: Registered adapter
Delivered by: PR-2.

- **WHEN** a supported adapter is selected
- **THEN** its factory validates its own adapterConfig and exposes send and stop while generic code does not depend on vendor fields

#### Scenario: Stable helper and adapter boundary
Delivered by: PR-1; adopted unchanged by PR-2.

- **WHEN** a deployment connects a collector to passive markers
- **THEN** IDs follow `^[a-z]+(?:\.[a-z]+){2,}$` and the manifest lifecycle; observeSkillhubIntents({ root, rules, onIntent }) returns stop; frozen DEFAULT_RULES contains the original 12 rules; resolveRules(rules) returns active and skipped entries with id and reason
- **AND** only click, submit and card rule types are supported with their documented host restrictions; invalid or unknown entries are skipped without affecting the page
- **AND** the intent is { id, event }, describes native intent rather than business success, and evolves additive-only within its version
- **AND** adapter send(intent) returns Promise<void> and never rejects, stop prevents new dispatch, and vendor parameters belong only to the adapter

#### Scenario: Configuration maps to the existing contract
Delivered by: PR-2.

- **WHEN** enabled, adapter, consent, rules and adapterConfig are supplied
- **THEN** enabled and consent gate optional loading and observation, adapter selects a static registered factory, rules use the existing resolver (omitted means defaults, explicit object replaces them), and adapterConfig is validated only by the selected adapter

### Requirement: Safe event lifecycle
Delivered by: PR-1 (example lifecycle); PR-2 (product controller and asynchronous module loading).

The system SHALL preserve finite configured rules, field allowlists, duplicate prevention, bounded loading waits and failure isolation.

#### Scenario: Stop races with asynchronous initialization
Delivered by: PR-1 (SDK wait/dispatch); PR-2 (module/controller races as well).

- **WHEN** collection is stopped while a module or SDK is loading
- **THEN** late initialization does not restart collection and no new request dispatches after stop returns, excluding requests already started

#### Scenario: Repeated initialization and rule changes
Delivered by: PR-1; preserved by PR-2.

- **WHEN** the same root is initialized repeatedly before stop
- **THEN** it returns the same stop and retains the first configuration and callback without duplicate events
- **AND** stop is idempotent, configuration changes require stop then reinitialization, and an old stop does not remove a new installation

### Requirement: Compatible deployment paths
Delivered by: PR-2 (normal deployment); PR-1 (opt-in edge example and mapping guide).

The system SHALL support root and supported sub-path deployments while preserving existing API, auth, upload, cache and security behavior.

#### Scenario: Large valid upload through normal Web
Delivered by: PR-2.

- **WHEN** a user uploads a business-valid package larger than 1 MiB through the standard Web entry with analytics enabled
- **THEN** optional analytics routes do not reduce the existing upload limit or alter the upload request

#### Scenario: Example edge preserves upload boundaries
Delivered by: PR-1.

- **WHEN** an operator uses the opt-in edge in front of the Web
- **THEN** client_max_body_size 100M applies at server level and is not lowered on application proxy locations; a 5 MiB API/upload probe reaches the upstream rather than returning edge 413, while a body larger than 100 MiB is rejected with 413
- **AND** any tighter collector-only limit does not change application uploads

#### Scenario: Example defaults match release Web
Delivered by: PR-1.

- **WHEN** SKILLHUB_WEB_UPSTREAM is omitted from the example Compose configuration
- **THEN** the edge targets host.docker.internal:80, matching the release WEB_PORT default of 80, and its extra_hosts provides host.docker.internal:host-gateway on native Linux
- **AND** the guide explains overrides and container-to-host reachability, while Docker Desktop/OrbStack already provide the hostname

#### Scenario: Optional endpoint boundary
Delivered by: PR-1 (example routes); PR-2 (generated product routes).

- **WHEN** a browser requests an unregistered analytics route
- **THEN** the route is rejected and does not expose the statistics platform administration or arbitrary upstream paths

### Requirement: Reproducible deployment documentation
Delivered by: PR-1 (manual example guide); PR-2 (configuration-only production guide).

The system SHALL document configuration fields, existing-platform and optional self-hosted deployment, required initialization, verification, troubleshooting, upgrade, disable and rollback.

#### Scenario: Operator follows the guide
Delivered by: PR-2.

- **WHEN** an operator deploys the supported integration using the published guide
- **THEN** no JS or Nginx source file edits are required, all manual platform prerequisites are explicit, and enabled, disabled and failure behavior can be independently verified

#### Scenario: Operator follows the PR-1 Chinese example guide
Delivered by: PR-1.

- **WHEN** an operator follows the Chinese integration guide without reading implementation source
- **THEN** the guide explicitly identifies the required entry configuration edits and platform initialization, explains both a custom send/stop adapter and self-hosted Umami, and provides copyable deployment and acceptance commands
- **AND** it explains the 12 defaults, finite rules and skipped diagnostics, upstream/host-gateway, supported sub-path mapping, CSP, actual Network payload and platform event checks, zero collection when disabled, troubleshooting, manifest/ID upgrades and shutdown
- **AND** best-effort/no retry, the 20-intent loading cap, pinned Umami version and remaining transport metadata are disclosed, with configuration-only deployment clearly deferred to PR-2

#### Scenario: Separate merges keep the issue open
Delivered by: PR-1 (scope declaration); PR-2 (completion gate).

- **WHEN** PR-1 merges independently
- **THEN** Issue #989 remains open until PR-2 configuration-only deployment is complete and verified
