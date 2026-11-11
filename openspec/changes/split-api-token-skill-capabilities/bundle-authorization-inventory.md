# Bundle historical authorization evidence inventory

Baseline: `a3dc2c4aa3de6c81c8332d1e133d31a0e907e4b9`.
This is a source/schema inventory, not an inspection of production data.

## Persisted evidence

- V54 creates `skill_suite_bundle_operation`: operation ID, preview token, client request ID,
  actor ID, target coordinates, reservation, archive digest/key, plan JSON, warning digest,
  status/failure/result IDs, timestamps and optimistic lock version.
  `SkillSuiteBundleExecutionOperation` maps these fields. There is no initiator type,
  API token ID, scope snapshot or persisted authorization decision.
- V54 preview rows contain actor ID, manifest/plan JSON, archive metadata, warning digest,
  expiration and confirmation timestamps. Preview `token` identifies a preview, not an API token.
- V54 member results link operation ID to coordinates, requested visibility/version,
  planned action, fingerprints, result IDs, errors/warnings and status. They contain no
  initiating authentication information.
- V55 adds staged-object cleanup metadata; V58 and V59 add actor/status/history indexes.
  No later migration in this checkout adds Bundle authorization provenance.
- `SkillSuiteBundleConfirmationAppService.confirm` receives actor ID and account/namespace
  roles. It persists the operation and member plan, marks the preview confirmed and emits
  `SkillSuiteBundleAdvanceRequestedEvent`. It does not write an audit record recording
  operation ID together with authentication type, token identity or scopes.
- `SkillSuiteBundleController` extracts user ID and platform/namespace roles, not token
  authentication metadata. `Idempotency-Key` is a caller-supplied request identifier, not
  evidence of token authentication.

## Audit and authentication records

- `audit_log` (V1, V6, V7, V42, V61; `AuditLog`, `AuditLogService`) stores actor, action,
  target type/numeric ID, request ID, client IP, user agent, details and time. V61 adds
  organization ID, string target reference and result. These columns could support a
  future linked record, but the Bundle confirmation/execution writers do not produce one.
- Skill/Suite lifecycle audit entries describe resource operations. Neither actor identity
  nor a resource ID proves how a particular Bundle operation was authorized.
- `api_token` stores current scope JSON, user/subject identity, token prefix/hash,
  expiration, revocation and last use. No Bundle foreign key or operation ID links it to
  the initiating request. Last-use time cannot identify an operation or recover its scopes.
- `ApiTokenAuthenticationFilter` projects scopes into transient Spring authorities, without
  persisting an operation link. `RequestLoggingFilter` logs method/path/status/timing/IP,
  not authenticated token identity or scopes; ordinary logs are not authorization evidence.
- Recovery resolves current account/namespace roles through
  `SkillSuiteBundleActorContextService`. Retry accepts the current caller's roles.
  Neither is evidence of the original Token capability ceiling.

## Resulting implementation rule

No reliable operation-ID-linked historical initiator/scope evidence is established by this
checkout. Do not backfill authority from dates, actor roles, current tokens, request IDs,
empty snapshots or worker/retry identities.

New operations must persist an immutable explicit SESSION or TOKEN initiator; TOKEN requires
an effective-capability snapshot before insertion. Existing rows retain unknown provenance.
If an unknown-provenance operation reaches a PRIVATE confirmation step, block the member and
operation as `BLOCKED_RETRYABLE` with an explicit authorization-provenance-unknown reason,
without calling `confirmPublish` or creating a partial Suite draft. Retry/recovery must use
only the original persisted authorization. A more privileged retry cannot repair missing
historical authority; the user must create a new explicitly authorized operation.

A future historical-evidence import would require independently verified, operation-linked
records proving initiator type and original authorization. No such import is justified here.
