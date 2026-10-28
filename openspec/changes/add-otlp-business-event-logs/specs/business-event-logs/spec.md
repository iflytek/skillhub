## ADDED Requirements

### Requirement: Business Logs SHALL be opt-in and independent of Trace

The application SHALL create a dedicated LoggerProvider only when `skillhub.observability.business-logs.enabled=true`. It SHALL NOT register global OpenTelemetry, bridge application logs, change trace sampling or start a trace exporter. Empty endpoint while enabled SHALL fail startup without disclosing configuration values.

#### Scenario: Default deployment
- **WHEN** no business Logs settings are provided
- **THEN** no business LoggerProvider, processor, exporter, worker or listener is created

#### Scenario: Trace independence
- **WHEN** business Logs is enabled with tracing `none`, `otel-sdk` or `external-agent`
- **THEN** business records are accepted independently of trace sampling
- **AND** only valid current span context is associated; request ID/MDC is never converted into trace identity

### Requirement: Existing business facts SHALL retain producer meaning

The event catalog in `docs/business-event-logs.md` SHALL define all included events, producer paths, occurrence boundary, actor and fields. Publication SHALL cover auto-publication, PRIVATE confirmation, review approval and promotion approval. Download preparation SHALL NOT mean transfer, installation or execution success.

#### Scenario: Commit and rollback
- **WHEN** a supported event is published within a physical transaction (excluding savepoint-level rollback semantics)
- **THEN** the adapter captures a scalar snapshot and trace context before handoff
- **AND** export admission occurs only after that physical transaction commits; its rollback emits no record

#### Scenario: Savepoint exclusion and independent transaction
- **WHEN** `PROPAGATION_NESTED` work dispatches an event and rolls back its savepoint, but the outer physical transaction commits
- **THEN** savepoint-level rollback suppression is explicitly outside the contract; the event can still export despite the nested write rolling back
- **AND** producers requiring savepoint suppression SHALL NOT dispatch within nested/savepoint work
- **WHEN** `REQUIRES_NEW` work commits and the suspended outer transaction later rolls back
- **THEN** the independently committed event is admitted and the outer transaction's event is suppressed

#### Scenario: Nontransactional download
- **WHEN** an authorized PUBLISHED download successfully builds its result without an outer transaction
- **THEN** one download-prepared record is admitted
- **AND** a rejected request, preview/review package, Suite plan or metadata poll emits no such record

#### Scenario: Promotion identities and namespace filters
- **WHEN** an initial or update promotion approval commits
- **THEN** publication SHALL identify the derived target skill/version/namespace, and the promotion decision SHALL identify the source skill/namespace with no invented version field
- **AND** a source-only namespace allowlist SHALL retain only the decision; a target-only allowlist SHALL retain only publication

### Requirement: Export SHALL have bounded, nonblocking business-thread cost

The adapter SHALL use asynchronous SDK batching and standard bounded exporter retry, never caller-runs network IO. Record count, batch size, attribute count/length, timeout, retry attempts/backoff and shutdown wait SHALL be bounded. Telemetry exceptions SHALL NOT propagate into business calls; failures SHALL be counted with bounded diagnostic logging.

#### Scenario: Receiver outage and saturation
- **WHEN** the receiver stalls or fails and capacity is exhausted
- **THEN** business calls do not wait for receiver IO or queue space
- **AND** admission drops and failed exports are observable through existing Actuator metrics

#### Scenario: Ambiguous acknowledgement
- **WHEN** an accepted batch is retried after an ambiguous network failure
- **THEN** the same `log.record.uid` survives retries
- **AND** receivers may deduplicate, without an exactly-once promise

#### Scenario: Abandoned export result
- **WHEN** an SDK export result never completes, including a truncated non-2xx response-body callback failure
- **THEN** the export-budget deadline SHALL fail the owned completion guard, release admission permits and count one failed batch
- **AND** result completion, deadline and close SHALL compete for exactly one cleanup; late results SHALL NOT release again or change counters
- **AND** later events SHALL be admitted and exported when the receiver recovers

### Requirement: Data export SHALL use a single explicit allowlist

A single mapper SHALL export only fixed documented scalar fields, including IDs of private resources. Namespace IDs and event names SHALL be operator-filterable. Human actors SHALL use only local opaque `user.id`; anonymous/unknown and system actors SHALL omit it. No entity serialization, enrichment queries, resource autodetection, content, credentials, URLs, IP/UA, request IDs or raw headers is permitted.

#### Scenario: Sensitive rejection fields
- **WHEN** a domain rejection carries arbitrary reason or submitter information
- **THEN** neither field is copied into business attributes or body

#### Scenario: Private resource scope
- **WHEN** a private resource is within the configured namespace/event allowlists
- **THEN** only IDs, visibility and documented operational fields are exported
- **AND** names, slugs and content remain excluded

#### Scenario: Context and diagnostic credential boundaries
- **WHEN** MDC and OTel Baggage contain distinct sensitive sentinels
- **THEN** neither keys nor values SHALL appear in exported attributes, body or resource
- **WHEN** receiver Status messages or transport exceptions echo transport credentials
- **THEN** shipped logging SHALL suppress raw `io.opentelemetry.exporter` and `io.opentelemetry.sdk.logs` diagnostics, including child loggers, by default
- **AND** fixed-text rate-bounded SkillHub diagnostics and counters SHALL remain available
- **AND** operator documentation SHALL warn that explicitly raising SDK logging opts into possible credential disclosure

#### Scenario: Uncaught asynchronous exporter failure
- **WHEN** the dedicated managed business exporter throws from an asynchronous response callback, including HTTP 401 with a malformed chunk-size line containing synthetic credential text
- **THEN** its private dispatcher exception boundary SHALL prevent raw throwable text from reaching stdout, stderr or application logs with the shipped logging defaults
- **AND** the adapter error counter and fixed warning SHALL remain available; the completion deadline SHALL count one failed batch and recover capacity for a later successful event
- **AND** the boundary SHALL exist before export and remain on late callbacks through dispatcher shutdown, without changing the JVM default uncaught handler
- **AND** unrelated threads (even same-named threads outside the private group) and non-dispatcher group members SHALL delegate unchanged to their previous parent/default handling
- **AND** documentation SHALL scope this guarantee to the verified managed business sender and logging defaults, excluding unrelated exporters/application failures, explicit logging/SDK overrides, exception-intercepting agents, heap dumps and receiver logs

### Requirement: Deployment and delivery limitations SHALL be explicit

User documentation SHALL include every configuration key/default/bound, direct and Collector deployment, Secret injection, loss/duplicate/shutdown semantics, SDK compatibility limits, troubleshooting, a reproducible real receiver/query example and an acceptance matrix separating executed from unverified scenarios.

#### Scenario: Graceful exit
- **WHEN** the process shuts down
- **THEN** new admission stops and queued records get only the configured bounded drain opportunity
- **AND** remaining records may be lost; already accepted receiver data cannot be recalled
