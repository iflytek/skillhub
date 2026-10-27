## ADDED Requirements

### Requirement: Preserve passive business semantics
The system SHALL preserve existing static action categories and business interactions without introducing analytics dependencies into business components.

#### Scenario: Existing business operation
- **WHEN** a user logs in, publishes, uploads, downloads or navigates with analytics disabled or unavailable
- **THEN** existing permissions, requests, keyboard behavior and business outcomes remain unchanged

### Requirement: Deploy using configuration only
The system SHALL deliver optional analytics modules with the Web artifact and accept deployment configuration without source edits or hand-written Nginx rules.

#### Scenario: Connect an existing supported platform
- **WHEN** the operator supplies valid analytics JSON, a supported adapter configuration and internal upstream origin
- **THEN** the Web startup generates safe runtime configuration and the required isolated proxy routes without an additional Nginx service

### Requirement: Default closed and fail isolated
The system SHALL default to analytics disabled and consent denied, and SHALL isolate invalid configuration and runtime analytics failures from Web and business availability.

#### Scenario: Disabled or denied collection
- **WHEN** configuration is omitted, enabled is false, or consent is denied
- **THEN** the browser does not load analytics modules or SDKs and does not send analytics requests

#### Scenario: Invalid configuration
- **WHEN** analytics JSON, adapter or upstream is invalid
- **THEN** only analytics is disabled with a diagnostic that does not echo raw values, while existing Web configuration remains usable

#### Scenario: Statistics service unavailable
- **WHEN** the upstream is unreachable or its hostname cannot resolve
- **THEN** the Web still starts and business remains usable without waiting for the statistics service

### Requirement: Vendor-neutral module contract
The system SHALL separate generic configuration, rules and lifecycle from adapter-specific parameters and SDK calls.

#### Scenario: Registered adapter
- **WHEN** a supported adapter is selected
- **THEN** its factory validates its own adapterConfig and exposes send and stop while generic code does not depend on vendor fields

### Requirement: Safe event lifecycle
The system SHALL preserve finite configured rules, field allowlists, duplicate prevention, bounded loading waits and failure isolation.

#### Scenario: Stop races with asynchronous initialization
- **WHEN** collection is stopped while a module or SDK is loading
- **THEN** late initialization does not restart collection and no new request dispatches after stop returns, excluding requests already started

### Requirement: Compatible deployment paths
The system SHALL support root and supported sub-path deployments while preserving existing API, auth, upload, cache and security behavior.

#### Scenario: Large valid upload through normal Web
- **WHEN** a user uploads a business-valid package larger than 1 MiB through the standard Web entry with analytics enabled
- **THEN** optional analytics routes do not reduce the existing upload limit or alter the upload request

#### Scenario: Optional endpoint boundary
- **WHEN** a browser requests an unregistered analytics route
- **THEN** the route is rejected and does not expose the statistics platform administration or arbitrary upstream paths

### Requirement: Reproducible deployment documentation
The system SHALL document configuration fields, existing-platform and optional self-hosted deployment, required initialization, verification, troubleshooting, upgrade, disable and rollback.

#### Scenario: Operator follows the guide
- **WHEN** an operator deploys the supported integration using the published guide
- **THEN** no JS or Nginx source file edits are required, all manual platform prerequisites are explicit, and enabled, disabled and failure behavior can be independently verified
